#!/bin/sh
set -e
FLAVOR=$1
./scripts/docker_images_scripts/load-docker-images.sh
cd deploy
export COMPOSE_BAKE=true
docker compose --env-file "../$FLAVOR.env" \ \
  -f deploy/docker-compose/docker-compose.yml \
  -f deploy/docker-compose/docker-compose.d.yml \
  up --build