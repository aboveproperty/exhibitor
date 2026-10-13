# IMPORTANT NOTE

Exhibitor is now on its own! PLEASE READ THIS NOTE: https://groups.google.com/forum/#!topic/exhibitor-users/PVkcd88mk8c

## DESCRIPTION
Exhibitor is a supervisor system for Apache ZooKeeper (http://zookeeper.apache.org/).


## DETAILS

Please see the doc at https://github.com/Netflix/exhibitor/wiki

## BUILDING

Exhibitor is built via Maven (https://maven.apache.org/). `mvn package`

To create the docker image, after building:

    podman build -t aps-exhibitor .

When you are ready, deployment of the tagged image to your ECR repository 
is outlined in the next section.

If you are testing locally then both a shared data data volume are neeed:
    
    podman volume create exhibitor-shared
    podman volume create exhibitor1-data

To verify you can launch a temporary local instance:

    podman run --rm --name exhibitor1 \
           -v exhibitor1-data:/data \
           -v exhibitor-shared:/mnt/zookeeper:z \
           -e DEPLOY_TYPE=="LOCAL" \
           -p 8181:8181 aps-exhibitor

Adjust `8181` to match an available port on your machine. Once running, your
instance may be accessed via http://localhost:8181.

## DEPLOYING

When ready for deployment you may choose the Docker container approach. This can be deployed
to Fargate; however, pay particular attention to the storage volume requirements.

*The most critical storage path is `/data` where the Zookeeper data is stored.*
This must be on a per-node volume that is persistent and should not be lost when
the instance is replaced.

|Directory | Description   |
|---|---|
| /opt/zookeeper/snapshots | Zookeeper storage for each node. This must be a persistent volume which can be assigned to a new Exhibitor instance when it is no longer in use. |
| /opt/zookeeper/transactions | Zookeeper log directory; local to each node. |
| /opt/exhibitor | Installation directory for Exhibitor |
| /opt/zookeeper | Installation directory for Zookeeper |


Tag the image for your new ECR repository:
    `docker tag aps-exhibitor:latest <AWS_ACCOUNT_ID>.dkr.ecr.<YOUR_REGION>.amazonaws.com/aps-exhibitor:latest`

Push the image to the cloud:
    `docker push <AWS_ACCOUNT_ID>.dkr.ecr.<YOUR_REGION>.amazonaws.com/aps-exhibitor:latest`

Configure the container deployment paying close attention to the variables defined 
in the docker file.

|Variable | Description   |
|---|---|
|DEPLOY_TYPE | Type of deployment; for example, in AWS this is "AWS" (no quotes) |
|ACCOUNT_ID | Human readable account identifier; for example "PROD" for production |
|AWS_ACCESS_KEY_ID | Access key for S3 bucket if instance type is AWS |
|AWS_SECRET_ACCESS_KEY | Secret access key for S3 bucket if instance type is AWS |
|S3_BUCKET | Computed from `Exhibitor-Config` via E2 tag if instance type is AWS|
|S3_PREFIX | Computed from `Exhibitor-Config` via E2 tag if instance type is AWS |
|HTTP_PROXY_HOST | If an S3 proxy for AWS is required then this hostname must be specified |
|HTTP_PROXY_PORT | If an S3 proxy for AWS is required then this port must be specified |
|HTTP_PROXY_USERNAME | If an S3 proxy for AWS is required then this username must be specified |
|HTTP_PROXY_PASSWORD | If an S3 proxy for AWS is required then this password must be specified |
|ZOOKEEPER_FILES_PATH | If using local file storage for coherence this is the path to shared data |
 
If you are require the HTTP Proxy then all HTTP_PROXY* values must be specified.
This is an unusual configuration and may typically be ignored.

## ARTIFACTS

Exhibitor binaries are published to Maven Central. Please see the docs for details.

## MAILING LIST

There is an Exhibitor mailing list. Join here: http://groups.google.com/group/exhibitor-users

## AUTHOR

Jordan Zimmerman (jordan@jordanzimmerman.com)

## LICENSE

Copyright 2012 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the “License”); you may not use this file except in
compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an “AS IS” BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing permissions and limitations under the
License.
