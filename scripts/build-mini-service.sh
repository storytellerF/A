sh scripts/build-server.sh
sh scripts/build-cli.sh
mkdir -p ./deploy/build
cp ./server/build/libs/*-all.jar ./deploy/build
cp ./cli/build/distributions/cli.zip ./deploy/build
cd deploy
docker compose --env-file ../mini.env \
  -f docker-compose.yml \
  -f docker-compose.d.yml \
  up -d --build