set -e
IS_HOST=true BUILD_ON_HOST=true sh scripts/build-server-on-condition.sh
docker build -t a-server:latest .
