cd deploy
COMPOSE_PROFILES=db,es,media docker compose --env-file ../dev.env up --build