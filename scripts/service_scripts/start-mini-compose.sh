#!/bin/sh

docker compose --env-file ../mini.env \
     -f docker-compose.yml \
     -f docker-compose.d.yml \
     up -d --build