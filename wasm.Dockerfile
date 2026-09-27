# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS builder

RUN apt-get update && \
    apt-get install -y --no-install-recommends libatomic1 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=false"

ARG FLAVOR
ARG BUILD_TYPE
ARG SERVER_URL
ARG WS_SERVER_URL

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    --mount=type=cache,target=/app/.gradle,sharing=locked \
    --mount=type=cache,target=/app/app/webApp/build \
    --mount=type=cache,target=/app/panel/webApp/build \
    test -n "$FLAVOR" && \
    test -n "$BUILD_TYPE" && \
    test -n "$SERVER_URL" && \
    test -n "$WS_SERVER_URL" && \
    ./gradlew \
        :app:webApp:wasmJsBrowserDistribution \
        :panel:webApp:wasmJsBrowserDistribution \
        -Ptarget.wasm=true \
        -Pserver.flavor="$FLAVOR" \
        -Pserver.buildType="$BUILD_TYPE" \
        -Papp.server.url="$SERVER_URL" \
        -Papp.ws.server.url="$WS_SERVER_URL" \
        --console=plain \
        --no-daemon && \
    mkdir -p deploy/build/app-wasm deploy/build/panel-wasm && \
    cp -a app/webApp/build/dist/wasmJs/productionExecutable/. deploy/build/app-wasm/ && \
    cp -a panel/webApp/build/dist/wasmJs/productionExecutable/. deploy/build/panel-wasm/

FROM nginx:1.27-alpine AS app-wasm

COPY deploy/docker-compose/app-wasm.nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/deploy/build/app-wasm/ /usr/share/nginx/html/

FROM nginx:1.27-alpine AS panel-wasm

COPY deploy/docker-compose/app-wasm.nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/deploy/build/panel-wasm/ /usr/share/nginx/html/
