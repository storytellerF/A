set -e
sh scripts/build-server-for-docker.sh
cd deploy
mkdir -p es_ca
docker compose --env-file ../prod.env \
  -f docker-compose.yml \
  -f docker-compose.dem.yml \
  up -d --build