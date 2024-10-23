set -e
PUSH_TO_REMOTE_URI=$1
REMOTE_CERT_FILE=$2
REMOTE_COMMAND=$3
FLAVOR=mini

if [ -z "$PUSH_TO_REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ] || [ -z "$REMOTE_COMMAND" ]; then
   cd deploy
   IS_HOST=true \
     IS_DOCKER=false \
     BUILD_ON_HOST=true \
     BUILD_ON_DOCKER=false \
     sh scripts/build_scripts/build-all-in-flavor.sh $FLAVOR true
   docker compose --env-file ../$FLAVOR.env \
     -f docker-compose.yml \
     -f docker-compose.d.yml \
     up -d --build
else
  IS_HOST=true \
    IS_DOCKER=false \
    BUILD_ON_HOST=true \
    BUILD_ON_DOCKER=false \
    sh scripts/build_scripts/build-all-in-flavor.sh $FLAVOR true
  sh scripts/service_scripts/start-service-on-remote.sh $PUSH_TO_REMOTE_URI $REMOTE_CERT_FILE "$REMOTE_COMMAND $FLAVOR"
fi
