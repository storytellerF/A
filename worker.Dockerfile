FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl unzip

WORKDIR /app
COPY . .
ENV HOST_TYPE=docker

ARG BUILD_TYPE
ARG FLAVOR
ARG BUILD_ON

RUN find scripts/ -type f \( -name "*.sh" -o -name "*.js" \) -exec sed -i 's/\r$//' {} + && \
    sed -i 's/\r$//' gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./scripts/build_scripts/build-on-condition.sh ${FLAVOR} ${BUILD_TYPE} ${BUILD_ON} \
    "./scripts/build_scripts/build-worker.sh"

RUN mkdir -p ./cloud/worker/build/decompressed && \
    tar -xf ./deploy/build/worker.tar -C ./cloud/worker/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev

WORKDIR /app

COPY --from=builder /app/cloud/worker/build/decompressed/worker .

ENTRYPOINT ["sh", "./bin/worker"]
# ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]

