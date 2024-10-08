set -e
sh scripts/build-server-for-docker.sh
cd deploy
mkdir -p es_ca
docker compose --env-file ../prod.env up -f docker-compose.yml -f docker-compose.dem.yml -d --build