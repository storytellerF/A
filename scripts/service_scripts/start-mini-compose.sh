#!/bin/sh

USE_PREBUILD=$1

if [ "$USE_PREBUILD" = "true" ]; then
     docker compose --env-file ../mini.env \
          -f docker-compose.yml \
          -f docker-compose.d.yml \
          -f docker-compose.prebuild.yml \
          up -d --build
else
     docker compose --env-file ../mini.env \
          -f docker-compose.yml \
          -f docker-compose.d.yml \
          up -d --build
fi
