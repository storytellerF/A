set -e
sh scripts/docker_images_scripts/load-docker-images.sh
cd deploy
docker compose \
  -f docker-compose.yml \
  -f docker-compose.d.yml \
  up --build