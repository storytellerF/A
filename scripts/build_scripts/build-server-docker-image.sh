set -e
DOCKER_IMAGE_NAME=$1
if [ -z "$DOCKER_IMAGE_NAME" ]; then
  echo "Error: DOCKER_IMAGE_NAME must be set."
  exit 1
fi

IS_HOST=true \
  IS_DOCKER=false \
  BUILD_ON_HOST=true \
  BUILD_ON_DOCKER=false \
  sh scripts/build_scripts/build-all-in-flavor.sh alpha true
docker build -t $DOCKER_IMAGE_NAME .
