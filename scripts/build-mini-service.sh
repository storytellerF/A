sh build-server.sh
sh build-cli.sh
cp ./server/build/libs/*-all.jar ./deploy
cp ./cli/build/distributions/cli.zip ./deploy
cd deploy
docker compose --env-file ../mini.env up -d --build