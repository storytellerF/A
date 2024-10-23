set -e
FLAVOR=alpha
sh scripts/build_scripts/build-all-in-flavor.sh $FLAVOR true
cd deploy
mkdir -p es_ca
docker compose --env-file ../$FLAVOR.env up -d --build