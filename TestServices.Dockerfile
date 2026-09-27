FROM eclipse-temurin:21-alpine AS runtime

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app
WORKDIR /app

FROM runtime AS filesystem-service
COPY --chown=app:app deploy/build/filesystem-service.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/filesystem-service"]

FROM runtime AS lucene-service
COPY --chown=app:app deploy/build/lucene-service.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/lucene-service"]

FROM runtime AS server
USER root
RUN apk add --no-cache libavif-dev font-noto-all
COPY --chown=app:app deploy/build/server.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/server"]

FROM runtime AS worker
USER root
RUN apk add --no-cache libavif-dev vulkan-loader
COPY --chown=app:app deploy/build/worker.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/worker"]

FROM runtime AS ws
USER root
RUN apk add --no-cache libavif-dev font-noto-all
COPY --chown=app:app deploy/build/ws.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/ws"]

FROM runtime AS cli
USER root
RUN apk add --no-cache libavif-dev font-noto-all netcat-openbsd
COPY --chown=app:app deploy/build/cli.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
COPY --chown=app:app scripts/docker/cli-entrypoint.sh ./scripts/docker/cli-entrypoint.sh
COPY --chown=app:app scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh
COPY --chown=app:app scripts/tool_scripts/terminal-log.sh ./scripts/tool_scripts/terminal-log.sh
USER app:app
ENTRYPOINT ["sh", "./scripts/docker/cli-entrypoint.sh"]
