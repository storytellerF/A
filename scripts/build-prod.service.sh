cd deploy
COMPOSE_PROFILES=db,es,media,bunker,web docker compose --env-file ../prod.env up --build