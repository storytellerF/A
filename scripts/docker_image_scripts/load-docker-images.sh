set -e
docker load -i deploy/docker-images/eclipse-temurin.tar
docker load -i deploy/docker-images/postgres.tar
docker load -i deploy/docker-images/adminer.tar
docker load -i deploy/docker-images/minio.tar
