#!/bin/sh
set -e
FLAVOR=$1
./scripts/docker_images_scripts/load-docker-images.sh
cd deploy
docker compose --env-file "../$FLAVOR.env" \ \
  -f docker-compose.yml \
  -f docker-compose.d.yml \
  up --build