cd deploy
mkdir -p es_ca
docker compose --env-file ../dev.env up -d --build