FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl dos2unix

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker

ARG BUILD_TYPE
ARG FLAVOR
ARG BUILD_ON

RUN --mount=type=cache,target=/root/.gradle \
    ./scripts/build_scripts/build-on-condition.sh "$BUILD_ON" \
    "./scripts/build_scripts/build-worker.sh $FLAVOR $BUILD_TYPE"

RUN mkdir -p ./cloud/worker/build/decompressed && \
    tar -xf ./deploy/build/worker.tar -C ./cloud/worker/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

USER app:app

WORKDIR /app

COPY --from=builder --chown=app:app /app/cloud/worker/build/decompressed/worker .

ENTRYPOINT ["sh", "./bin/worker"]
# ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]
