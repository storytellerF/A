set -e
sh scripts/load-docker-images.sh
cd deploy
docker compose up -f docker-compose.yml -f docker-compose.d.yml --build