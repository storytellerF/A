#!/bin/bash
set -e
FLAVOR=alpha
./scripts/build_scripts/build-all-in-flavor.sh $FLAVOR true
cd deploy
mkdir -p es_ca
docker compose --env-file ../$FLAVOR.env up -d --build