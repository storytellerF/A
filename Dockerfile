# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

ARG BUILDER_STAGE=source-builder

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS source-builder

RUN apk add --no-cache bash curl

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

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
    sh scripts/build_scripts/stage-distributions.sh

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS prebuilt-builder
WORKDIR /app
COPY . .
RUN sh scripts/build_scripts/stage-distributions.sh

FROM ${BUILDER_STAGE} AS builder

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS server

RUN apk add --no-cache libavif-dev font-noto-all

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
RUN mkdir -p /home/app/logs/server && chown -R app:app /home/app/logs
ENV HOME=/home/app
ENV LOG_PATH=/home/app/logs/server

USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/server/ .
ENTRYPOINT ["sh", "./bin/server"]

FROM eclipse-temurin:21-jre-jammy@sha256:f04fb34e053148344e83317976114ec3f37e4b830ec8bdab5a2fe3cecd7d010b AS worker

RUN apt-get update && \
    apt-get install -y --no-install-recommends libavif-dev libvulkan1 && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ARG APP_UID=1000
ARG APP_GID=1000
RUN groupadd --gid "$APP_GID" app && \
    useradd --uid "$APP_UID" --gid app --home-dir /home/app --create-home --shell /usr/sbin/nologin app
RUN mkdir -p /home/app/logs/worker && chown -R app:app /home/app/logs
ENV HOME=/home/app
ENV LOG_PATH=/home/app/logs/worker

USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/worker/ .
ENTRYPOINT ["sh", "./bin/worker"]

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS ws

RUN apk add --no-cache libavif-dev font-noto-all

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
RUN mkdir -p /home/app/logs/ws && chown -R app:app /home/app/logs
ENV HOME=/home/app
ENV LOG_PATH=/home/app/logs/ws

USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/ws/ .
ENTRYPOINT ["sh", "./bin/ws"]

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS cli

RUN apk add --no-cache libavif-dev font-noto-all netcat-openbsd

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
RUN mkdir -p /home/app/logs/cli && chown -R app:app /home/app/logs
ENV HOME=/home/app
ENV LOG_PATH=/home/app/logs/cli

WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/cli/ .
COPY --from=builder --chown=app:app /app/scripts/docker/cli-entrypoint.sh ./scripts/docker/cli-entrypoint.sh
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/terminal-log.sh ./scripts/tool_scripts/terminal-log.sh

USER app:app
ENTRYPOINT ["sh", "./scripts/docker/cli-entrypoint.sh"]

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS filesystem-service

RUN apk add --no-cache wget

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app
USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/filesystem-service/ .
ENTRYPOINT ["sh", "./bin/filesystem-service"]

FROM eclipse-temurin:21-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS lucene-service

RUN apk add --no-cache wget

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app
USER app:app
WORKDIR /app
COPY --from=builder --chown=app:app /app/deploy/staged/lucene-service/ .
ENTRYPOINT ["sh", "./bin/lucene-service"]
