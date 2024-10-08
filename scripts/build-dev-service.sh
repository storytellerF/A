cd deploy
mkdir -p es_ca
docker compose --env-file ../dev.env \
  -f docker-compose.yml \
  -f docker-compose.dem.yml \
  up -d --build