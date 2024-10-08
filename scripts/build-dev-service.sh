cd deploy
mkdir -p es_ca
docker compose --env-file ../dev.env up -f docker-compose.yml -f docker-compose.dem.yml -d --build