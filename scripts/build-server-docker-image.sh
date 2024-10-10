set -e
sh IS_HOST=true BUILD_ON_HOST=true scripts/build-server-for-docker.sh
docker build -t a-server:latest .