#!/bin/bash
set -e
FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

sh "scripts/service_scripts/build-service.sh $FLAVOR"