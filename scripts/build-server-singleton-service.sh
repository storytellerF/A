set -e
sh scripts/build-server-for-docker.sh
cd deploy
docker build -t a-server .
docker run -d a-server