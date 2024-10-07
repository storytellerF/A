set -e
docker load -i deploy/docker-images/jdk17.tar
docker load -i deploy/docker-images/jre17.tar
docker load -i deploy/docker-images/postgres.tar
docker load -i deploy/docker-images/adminer.tar
docker load -i deploy/docker-images/minio.tar