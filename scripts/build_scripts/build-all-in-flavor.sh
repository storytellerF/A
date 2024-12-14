#!/bin/sh

set -e
FLAVOR=$1
IS_PROD=$2

if [ -z "$FLAVOR" ] || [ -z "$IS_PROD" ]; then
  echo "FLAVOR and IS_PROD must be set"
  exit 1
fi

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$IS_PROD"

./scripts/build_scripts/build-server-on-condition.sh