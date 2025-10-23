#!/bin/bash
FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi
cd "a-server/$FLAVOR" || exit
cd ../..
cd ./Projects/AData && git pull
cd ./Projects/A && git pull
mkdir -p ./cloud/server/build/libs \
  ./cloud/cli/build/distributions \
  ./cloud/cli/build/distributions
cp "./a-server/$FLAVOR/server-all.jar" ./cloud/server/build/libs/server-all.jar
cp "./a-server/$FLAVOR/cli.tar" ./cloud/cli/build/distributions/cli.tar
cp "./a-server/$FLAVOR/worker.tar" ./cloud/worker/build/distributions/worker.tar

export BUILD_ON=local
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'