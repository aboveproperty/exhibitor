# Pull the official AWS CLI components
FROM amazon/aws-cli:latest AS aws-cli-source
FROM amazoncorretto:8-al2023-jdk

# base configuration
ARG ZK_VERSION=zookeeper-3.4.6
ARG ZK_FILE=$ZK_VERSION.tar.gz
ARG EXHIBITOR_FILE=exhibitor-standalone-1.7.1-SNAPSHOT-jar-with-dependencies.jar

# Environment Configuration
# -------------------------
# INSTANCE_TYPE can be either of the following:
#   AWS   - 169.254.169.254 must be available
#   LOCAL - $ZOOKEEPER_FILES_PATH must exist (shared filesystem)
#
# ACCOUNT_ID        - type of account in human readable terms (eg: "prod")
#
# ZK_DATA_DIR       - data directory for Zookeeper
# ZK_LOG_DIR        - transaction log directory for Zookeeper
# ZK_ENSEMBLE_SIZE  - size of ZK ensemble
#
ENV INSTANCE_TYPE=AWS
ENV ZOOKEEPER_FILES_PATH=/mnt/zookeeper
ENV ACCOUNT_ID=local

# useful ZK settings
ENV ZK_DATA_DIR=/data
ENV ZK_LOG_DIR=/opt/zookeeper/transactions
ENV ZK_ENSEMBLE_SIZE=0

# Copy the AWS CLI binary and its embedded dependencies straight over
COPY --from=aws-cli-source /usr/local/aws-cli/ /usr/local/aws-cli/
COPY --from=aws-cli-source /usr/local/bin/ /usr/local/bin/

# install dependencies for this container
RUN dnf install -y gzip tar jq iproute hostname && dnf clean all

# configure zookeeper
RUN cd /tmp && curl -L -O http://archive.apache.org/dist/zookeeper/$ZK_VERSION/$ZK_FILE
RUN cd /opt && tar zxf /tmp/$ZK_FILE
RUN rm /tmp/$ZK_FILE

RUN cd /opt && ln -s $ZK_VERSION zookeeper

# create additional zk directories
RUN mkdir -p /opt/zookeeper/transactions

# create exhibitor
RUN mkdir -p /opt/exhibitor
COPY exhibitor-standalone/target/$EXHIBITOR_FILE /opt/exhibitor/exhibitor.jar

EXPOSE 2181 2888 3888 8181

COPY exhibitor-standalone/src/main/docker/entrypoint.sh /opt/exhibitor/
WORKDIR /opt/exhibitor

ENTRYPOINT ["/bin/bash", "/opt/exhibitor/entrypoint.sh"]