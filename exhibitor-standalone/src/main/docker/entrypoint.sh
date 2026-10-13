#! /bin/bash -e

set -o pipefail

# Generates the default exhibitor config and launches exhibitor
warn() {
	echo "$1" >&2
}

die() {
	warn "ERROR: $1"
	exit 1
}

get_hostname() {
  if [ "$1" == "LOCAL" ]; then
    echo $(hostname -I)
  else
    echo $(ec2_metadata --local-ipv4 | awk '{print $NF}')
  fi
}

ec2_metadata() {
    local flag=$1
    local token=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")

    case "$flag" in
        -i|--instance-id)
            echo -n "instance-id: "; curl -s -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/instance-id; echo
            ;;
        -t|--instance-type)
            echo -n "instance-type: "; curl -s -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/instance-type; echo
            ;;
        -o|--local-ipv4)
            echo -n "availability-zone: "; curl -s -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/local-ipv4; echo
            ;;
        *)
            echo "To see all endpoints, run:"
            curl -s -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/
            ;;
    esac
}

# wait for smilodon to mount /data.
# Note the space in the grep so we don't get confused by
# potential future /data2 etc
#
# This will also allow us time to get eth1, which is our
# listen interface (see /etc/default/solr.in.sh)

shopt -s nocasematch

until grep -qs '/data ' /proc/mounts; do
  echo "Waiting for /data"
	sleep 15
done

# Check if eth1 exists and has an assigned IPv4 address
if ip addr show dev eth1 >/dev/null 2>&1 && ip -o -4 addr show dev eth1 | grep -q 'inet'; then
    INTERFACE="eth1"
else
    INTERFACE="eth0"
fi

PRIVATE_ADDRESS=$(ip -4 addr show up | grep -v '127.0.0.1' | awk '/inet / {print $2}' | cut -d/ -f1 | head -n1)
LISTEN_ADDRESS=$(ip -4 addr show ${INTERFACE} | grep "inet\b" | awk '{print $2}' | cut -d/ -f1)

# running in AWS?
if [ "$DEPLOY_TYPE" == "AWS" ]; then

    echo "Configure AWS"
    AWS_REGION=$(curl --silent http://169.254.169.254/latest/dynamic/instance-identity/document | jq -r .region)
    INSTANCE_ID=$(curl --silent http://169.254.169.254/latest/dynamic/instance-identity/document | jq -r .instanceId)

    echo " AWS_REGION: $AWS_REGION" >&2
    echo " INSTANCE_ID: $INSTANCE_ID" >&2

    if [[ -z "$AWS_REGION" || -z "$INSTANCE_ID" ]]; then
        die "AWS_REGION and INSTANCE_ID Required"
    fi

    EXHIBITOR_CONFIG=$(aws ec2 describe-tags --region $AWS_REGION --filters "Name=resource-id,Values=$INSTANCE_ID" | jq -r '.Tags[] | select(.["Key"] == "Exhibitor-Config") | .Value')
    S3_BUCKET=$(echo $EXHIBITOR_CONFIG | awk -F: '{print $1}')
    S3_PREFIX=$(echo $EXHIBITOR_CONFIG | awk -F: '{print $2}')
    ACCOUNT=$(aws sts get-caller-identity --query Account --output text)

    # verify all values are setup...
    if [[ ! -v EXHIBITOR_CONFIG || ! -v S3_BUCKET || ! -v S3_PREFIX ]]; then
        echo " EXHIBITOR_CONFIG: $EXHIBITOR_CONFIG" >&2
        echo " S3_BUCKET: $S3_BUCKET" >&2
        echo " S3_PREFIX: $S3_PREFIX" >&2
        die "AWS Not Configured" >&2
    fi

    case $ACCOUNT in
    963931055713)
      ACCOUNT_ID=abvprp-prod
      ;;
    795569841461)
      ACCOUNT_ID=abvprp-stage
      ;;
    717626221026)
      ACCOUNT_ID=abvprp-dev
      ;;
    377626641391)
      ACCOUNT_ID=rm-stage
      ;;
    362086317309)
      ACCOUNT_ID=rm-prod
      ;;
    *)
      die "Unsupported AWS account ID $ACCOUNT"
      ;;
    esac

elif [ "$DEPLOY_TYPE" == "LOCAL" ]; then
  ACCOUNT_ID=abvprp-local
  AWS_REGION="LOCAL"
fi

JAVA_OPTS="-Djava.awt.headless=true -Xms512m -Xmx512m -XX:+UseConcMarkSweepGC"

MISSING_VAR_MESSAGE="must be set"
DEFAULT_AWS_REGION="us-west-2"
DEFAULT_DATA_DIR="/opt/zookeeper/snapshots"
DEFAULT_LOG_DIR="/opt/zookeeper/transactions"
DEFAULT_ZK_ENSEMBLE_SIZE=0
S3_SECURITY=""
HTTP_PROXY=""
HOSTNAME=$(get_hostname $DEPLOY_TYPE)
: ${HOSTNAME:=?$MISSING_VAR_MESSAGE}
: ${AWS_REGION:=$DEFAULT_AWS_REGION}
: ${ZK_DATA_DIR:=$DEFAULT_DATA_DIR}
: ${ZK_LOG_DIR:=$DEFAULT_LOG_DIR}
: ${ZK_ENSEMBLE_SIZE:=$DEFAULT_ZK_ENSEMBLE_SIZE}
: ${HTTP_PROXY_HOST:=""}
: ${HTTP_PROXY_PORT:=""}
: ${HTTP_PROXY_USERNAME:=""}
: ${HTTP_PROXY_PASSWORD:=""}

cat <<-EOF >/opt/exhibitor/defaults.conf
	zookeeper-data-directory=$ZK_DATA_DIR
	zookeeper-install-directory=/opt/zookeeper/
	zookeeper-log-directory=$ZK_LOG_DIR
	log-index-directory=$ZK_LOG_DIR
	cleanup-period-ms=300000
	check-ms=30000
	backup-period-ms=600000
	client-port=2181
	cleanup-max-files=20
	backup-max-store-ms=21600000
	connect-port=2888
	observer-threshold=0
	election-port=3888
	auto-manage-instances-settling-period-ms=0
	zoo-cfg-extra=tickTime\=2000&initLimit\=10&syncLimit\=5&quorumListenOnAllIPs\=true
	auto-manage-instances=1
EOF

if [[ -n ${AWS_ACCESS_KEY_ID} ]]; then
	cat <<-EOF >/opt/exhibitor/credentials.properties
		 com.netflix.exhibitor.s3.access-key-id=${AWS_ACCESS_KEY_ID}
		 com.netflix.exhibitor.s3.access-secret-key=${AWS_SECRET_ACCESS_KEY}
	EOF
	S3_SECURITY="--s3credentials /opt/exhibitor/credentials.properties"
fi

if [[ -n ${S3_BUCKET} ]]; then
	echo "backup-extra=throttle\=&bucket-name\=${S3_BUCKET}&key-prefix\=${S3_PREFIX}&max-retries\=4&retry-sleep-ms\=30000" >>/opt/exhibitor/defaults.conf
	BACKUP_CONFIG="--configtype s3 --s3config ${S3_BUCKET}:${S3_PREFIX} ${S3_SECURITY} --s3region ${AWS_REGION} --s3backup true"
else
  [ -d "$ZOOKEEPER_FILES_PATH" ] || die "Directory does not exist: ${ZOOKEEPER_FILES_PATH}"
	BACKUP_CONFIG="--configtype file --fsconfigdir ${ZOOKEEPER_FILES_PATH} --filesystembackup true"
fi

if [[ -n ${ZK_PASSWORD} ]]; then
	SECURITY="--security web.xml --realm Zookeeper:realm --remoteauth basic:zk"
	echo "zk: ${ZK_PASSWORD},zk" >realm
fi

if [[ -n $HTTP_PROXY_HOST ]]; then
	cat <<-EOF >/opt/exhibitor/proxy.properties
		com.netflix.exhibitor.s3.proxy-host=${HTTP_PROXY_HOST}
		com.netflix.exhibitor.s3.proxy-port=${HTTP_PROXY_PORT}
		com.netflix.exhibitor.s3.proxy-username=${HTTP_PROXY_USERNAME}
		com.netflix.exhibitor.s3.proxy-password=${HTTP_PROXY_PASSWORD}
	EOF

	HTTP_PROXY="--s3proxy=/opt/exhibitor/proxy.properties"
fi

exec 2>&1

java $JAVA_OPTS -jar /opt/exhibitor/exhibitor.jar \
	--port 8181 --defaultconfig /opt/exhibitor/defaults.conf \
	--headingtext "Exhibitor for $ACCOUNT_ID $AWS_REGION" \
	${BACKUP_CONFIG} \
	${HTTP_PROXY} \
	--hostname ${LISTEN_ADDRESS} \
	${SECURITY}
