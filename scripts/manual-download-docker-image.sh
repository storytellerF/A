set -e
mkdir -p deploy/docker-images
curl -o download-image.sh https://raw.githubusercontent.com/moby/moby/master/contrib/download-frozen-image-v2.sh

bash download-image.sh ubuntu ubuntu:latest
tar -C 'ubuntu' -cf 'deploy/docker-images/ubuntu.tar' .

bash download-image.sh postgres postgres:latest
tar -C 'postgres' -cf 'deploy/docker-images/postgres.tar' .

bash download-image.sh adminer adminer:latest
tar -C 'adminer' -cf 'deploy/docker-images/adminer.tar' .

bash download-image.sh minio minio/minio:latest
tar -C 'minio' -cf 'deploy/docker-images/minio.tar' .
