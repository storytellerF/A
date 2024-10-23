set -e

# 判断 IS_HOST/IS_DOCKER 和 BUILD_ON_HOST/BUILD_ON_DOCKER 是否设置
if [ -z "$IS_HOST" ] || [ -z "$IS_DOCKER" ] || [ -z "$BUILD_ON_HOST" ] || [ -z "$BUILD_ON_DOCKER" ]; then
  echo "Error: IS_HOST/IS_DOCKER and BUILD_ON_HOST/BUILD_ON_DOCKER must be set."
  exit 1
fi

if [ "$BUILD_ON_HOST" = "true" ]; then
  if [ "$IS_HOST" = "true" ]; then
    echo "Both BUILD_ON_HOST and IS_HOST are true, proceeding with build..."
  else
    echo "Error: BUILD_ON_HOST is true but IS_HOST is not true. Cannot proceed with build."
    exit 0
  fi
elif [ "$BUILD_ON_DOCKER" = "true" ]; then
  if [ "$IS_DOCKER" = "true" ]; then
    echo "Both BUILD_ON_DOCKER and IS_DOCKER are true, proceeding with build..."
  else
    echo "Error: BUILD_ON_DOCKER is true but IS_DOCKER is not true. Cannot proceed with build."
    exit 0
  fi
else
  echo "Skip build."
  exit 0
fi

mkdir -p deploy/build
sh scripts/build_scripts/build-server.sh && sh scripts/build_scripts/build-cli.sh
