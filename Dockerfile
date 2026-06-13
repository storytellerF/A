FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker

ARG BUILD_TYPE
ARG FLAVOR
ARG BUILD_ON

RUN --mount=type=cache,target=/root/.gradle ./scripts/build_scripts/build-cloud-on-condition.sh ${FLAVOR} ${BUILD_TYPE} ${BUILD_ON}

RUN mkdir -p ./deploy/build/decompressed && \
    tar -xf ./deploy/build/cli.tar --strip-components=1 -C ./deploy/build/decompressed && \
    tar -xf ./deploy/build/server.tar --strip-components=1 -C ./deploy/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev font-noto-all

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

USER app:app

WORKDIR /app

COPY --from=builder --chown=app:app /app/deploy/build/decompressed .
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/terminal-log.sh ./scripts/tool_scripts/terminal-log.sh

ENTRYPOINT ["sh", "./bin/server"]
#ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]
