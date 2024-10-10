set -e
mkdir -p deploy/docker-images
curl -o download-image.sh https://raw.githubusercontent.com/moby/moby/master/contrib/download-frozen-image-v2.sh

bash download-image.sh eclipse-temurin eclipse-temurin:17
tar -C 'eclipse-temurin' -cf 'deploy/docker-images/eclipse-temurin.tar' .

bash download-image.sh postgres postgres:latest
tar -C 'postgres' -cf 'deploy/docker-images/postgres.tar' .

bash download-image.sh adminer adminer:latest
tar -C 'adminer' -cf 'deploy/docker-images/adminer.tar' .

bash download-image.sh minio minio/minio:latest
tar -C 'minio' -cf 'deploy/docker-images/minio.tar' .
