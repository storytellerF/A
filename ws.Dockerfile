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
    "./scripts/build_scripts/build-ws.sh $FLAVOR $BUILD_TYPE"

RUN mkdir -p ./cloud/ws/build/decompressed && \
    tar -xf ./deploy/build/ws.tar -C ./cloud/ws/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev font-noto-all

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app

USER app:app

WORKDIR /app

COPY --from=builder --chown=app:app /app/cloud/ws/build/decompressed/ws .

ENTRYPOINT ["sh", "./bin/ws"]
