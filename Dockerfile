FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker

#if($koyeb) #fun(envAndArg)
#if($koyeb) #fun(saveEnvTofile)
#startif($koyeb)
#else
ARG BUILD_TYPE
ARG FLAVOR
ARG BUILD_ON
#endif

#if($koyeb) RUN ./scripts/download_scripts/download-preset-data.sh

RUN --mount=type=cache,target=/root/.gradle ./scripts/build_scripts/build-cloud-on-condition.sh ${FLAVOR} ${BUILD_TYPE} ${BUILD_ON}

RUN mkdir -p ./deploy/build/decompressed && \
    tar -xf ./deploy/build/cli.tar --strip-components=1 -C ./deploy/build/decompressed && \
    tar -xf ./deploy/build/server.tar --strip-components=1 -C ./deploy/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev font-noto-all

ARG APP_UID=10001
ARG APP_GID=10001
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

WORKDIR /app

COPY --from=builder --chown=app:app /app/deploy/build/decompressed .
#if($koyeb) COPY --from=builder --chown=app:app /app/deploy ./deploy
# 使用koyeb 需要把args 变成env 后文件导入
#if($koyeb) COPY --from=builder --chown=app:app /app/build/envs/*.env .
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh
COPY --from=builder --chown=app:app /app/scripts/tool_scripts/terminal-log.sh ./scripts/tool_scripts/terminal-log.sh

USER app:app

ENTRYPOINT ["sh", "./bin/server"]
#ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]
