set -e
FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exet 1
fi

sh scripts/service_scripts/build-$FLAVOR-service.sh