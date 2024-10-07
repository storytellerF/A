set -e
mkdir -p deploy/docker-images
curl -o download-image.sh https://raw.githubusercontent.com/moby/moby/master/contrib/download-frozen-image-v2.sh

bash download-image.sh jdk17 openjdk:17-jdk
tar -C 'jdk17' -cf 'deploy/docker-images/jdk17.tar' .

bash download-image.sh jre17 openjdk:17
tar -C 'jre17' -cf 'deploy/docker-images/jre17.tar' .

bash download-image.sh postgres postgres:latest
tar -C 'postgres' -cf 'deploy/docker-images/postgres.tar' .

bash download-image.sh adminer adminer:latest
tar -C 'adminer' -cf 'deploy/docker-images/adminer.tar' .

bash download-image.sh minio minio/minio:latest
tar -C 'minio' -cf 'deploy/docker-images/minio.tar' .
