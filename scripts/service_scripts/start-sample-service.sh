#!/bin/bash
set -euo pipefail

SAMPLE_ENV_FILE="./deploy/sample.env"
SERVER_URL=$(grep '^SERVER_URL=' "$SAMPLE_ENV_FILE" | cut -d '=' -f2-)
WS_SERVER_URL=$(grep '^WS_SERVER_URL=' "$SAMPLE_ENV_FILE" | cut -d '=' -f2-)
BUILD_TYPE=$(grep '^BUILD_TYPE=' "$SAMPLE_ENV_FILE" | cut -d '=' -f2-)

if [ -z "$SERVER_URL" ] || [ -z "$WS_SERVER_URL" ] || [ -z "$BUILD_TYPE" ]; then
    echo "BUILD_TYPE, SERVER_URL, and WS_SERVER_URL must be set in $SAMPLE_ENV_FILE."
    exit 1
fi

if ! "${BUILD_SERVER_SCRIPT:-./scripts/build_scripts/build-server.sh}" || \
    ! "${BUILD_WS_SCRIPT:-./scripts/build_scripts/build-ws.sh}" || \
    ! "${BUILD_CLI_SCRIPT:-./scripts/build_scripts/build-cli.sh}"; then
    echo "Failed to build the sample server services on the host."
    exit 1
fi

if ! "${GRADLEW:-./gradlew}" :app:composeApp:wasmJsBrowserDistribution \
    -Ptarget.wasm=true \
    -Pserver.flavor=sample \
    -Pserver.buildType="$BUILD_TYPE" \
    -Papp.server.url="$SERVER_URL" \
    -Papp.ws.server.url="$WS_SERVER_URL"; then
    echo "Failed to build the Wasm distribution for the sample profile."
    exit 1
fi

./scripts/service_scripts/compose-service.sh sample false 'up -d --build --remove-orphans'
