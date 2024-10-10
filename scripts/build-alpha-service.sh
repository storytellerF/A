set -e
sh IS_HOST=true scripts/build-server_on_condition.sh
cd deploy
mkdir -p es_ca
docker compose --env-file ../alpha.env up -d --build