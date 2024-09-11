sh gradlew server:buildFatJar
cp ./server/build/libs/*-all.jar ./deploy
cd deploy
COMPOSE_PROFILES=db,media,web docker compose up --build