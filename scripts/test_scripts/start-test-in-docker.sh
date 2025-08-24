#!/usr/bin/env sh
set -e
cd ../android-in-docker && ./build-image.sh && cd ../A || exit
cd ./scripts/test_scripts
docker volume inspect sdk_data >/dev/null 2>&1 || docker volume create sdk_data
docker volume inspect gradle_data >/dev/null 2>&1 || docker volume create gradle_data
docker compose up -d --build
#docker build -t a-test:latest -f test.Dockerfile .
#docker run -it --name a-test a-test:latest