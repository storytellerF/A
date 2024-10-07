set -e
sh scripts/load-docker-images.sh
cd deploy
docker compose up --build