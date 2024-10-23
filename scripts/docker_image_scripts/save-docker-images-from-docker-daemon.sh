docker save -o deploy/docker-images/eclipse-temurin.tar eclipse-temurin:21
docker save -o deploy/docker-images/postgres.tar postgres:latest
docker save -o deploy/docker-images/adminer.tar adminer:latest
docker save -o deploy/docker-images/minio.tar minio/minio:latest