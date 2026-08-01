FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl dos2unix

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker

ARG BUILD_ON

RUN --mount=type=cache,target=/root/.gradle \
    ./scripts/build_scripts/build-on-condition.sh "$BUILD_ON" \
    "./scripts/build_scripts/build-worker.sh"

RUN mkdir -p ./cloud/worker/build/decompressed && \
    tar -xf ./deploy/build/worker.tar -C ./cloud/worker/build/decompressed

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends libavif-dev libvulkan1 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ARG APP_UID=1000
ARG APP_GID=1000
RUN groupadd --gid "$APP_GID" app && \
    useradd --uid "$APP_UID" --gid app --home-dir /home/app --create-home --shell /usr/sbin/nologin app
ENV HOME=/home/app

USER app:app

WORKDIR /app

COPY --from=builder --chown=app:app /app/cloud/worker/build/decompressed/worker .

ENTRYPOINT ["sh", "./bin/worker"]
# ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]
