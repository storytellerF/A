sh build-server.sh
cp ./server/build/libs/*-all.jar ./deploy
cd deploy
docker compose --env-file ../mini.env up --build