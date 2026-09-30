# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-alpine AS builder

RUN apk add --no-cache bash curl

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=false"

ARG BUILD_ON

RUN --mount=type=cache,id=cloud-gradle-alpine,target=/root/.gradle,sharing=locked \
    --mount=type=cache,id=cloud-project-gradle-alpine,target=/app/.gradle,sharing=locked \
    --mount=type=cache,id=cloud-server-build,target=/app/cloud/server/build \
    --mount=type=cache,id=cloud-worker-build,target=/app/cloud/worker/build \
    --mount=type=cache,id=cloud-ws-build,target=/app/cloud/ws/build \
    --mount=type=cache,id=cloud-cli-build,target=/app/cloud/cli/build \
    --mount=type=cache,id=cloud-filesystem-service-build,target=/app/cloud/filesystem-service/build \
    --mount=type=cache,id=cloud-lucene-service-build,target=/app/cloud/lucene-service/build \
    ./scripts/build_scripts/build-on-condition.sh "$BUILD_ON" \
    "./scripts/build_scripts/build-cloud.sh" && \
    for service in server worker ws cli filesystem-service lucene-service; do \
        archive="deploy/build/$service.tar"; \
        if [ -f "$archive" ]; then \
            mkdir -p "deploy/staged/$service"; \
            tar -xf "$archive" --strip-components=1 -C "deploy/staged/$service"; \
        fi; \
    done

FROM eclipse-temurin:21-alpine AS server

RUN apk add --no-cache libavif-dev font-noto-all

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/server/ .
ENTRYPOINT ["sh", "./bin/server"]

FROM eclipse-temurin:21-alpine AS worker

RUN apk add --no-cache libavif-dev vulkan-loader

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/worker/ .
ENTRYPOINT ["sh", "./bin/worker"]

FROM eclipse-temurin:21-alpine AS ws

RUN apk add --no-cache libavif-dev font-noto-all

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/ws/ .
ENTRYPOINT ["sh", "./bin/ws"]

FROM eclipse-temurin:21-alpine AS cli

RUN apk add --no-cache libavif-dev font-noto-all netcat-openbsd

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/cli/ .
COPY --from=builder --chown=app:app /app/scripts/docker/cli-entrypoint.sh ./scripts/docker/cli-entrypoint.sh
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/terminal-log.sh ./scripts/tool_scripts/terminal-log.sh

USER app:app
ENTRYPOINT ["sh", "./scripts/docker/cli-entrypoint.sh"]

FROM eclipse-temurin:21-alpine AS filesystem-service

RUN apk add --no-cache wget

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app
USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/filesystem-service/ .
ENTRYPOINT ["sh", "./bin/filesystem-service"]

FROM eclipse-temurin:21-alpine AS lucene-service

RUN apk add --no-cache wget

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app
USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/lucene-service/ .
ENTRYPOINT ["sh", "./bin/lucene-service"]
