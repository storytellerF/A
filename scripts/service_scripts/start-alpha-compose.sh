#!/bin/sh
set -e
FLAVOR=alpha
cd deploy
mkdir -p es_ca
docker compose --env-file ../$FLAVOR.env up -d --build