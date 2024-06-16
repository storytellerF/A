sh gradlew server:buildFatJar
cp ./server/build/libs/*-all.jar ./docker-context
COMPOSE_PROFILES=db,media,web docker compose --env-file mini.env up --build