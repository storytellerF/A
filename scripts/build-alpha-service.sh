set -e
#sh scripts/build-server-for-docker.sh
cd deploy
mkdir -p es_ca
docker compose --env-file ../alpha.env up -d --build