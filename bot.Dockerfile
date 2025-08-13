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
    "./scripts/build_scripts/build-bot.sh"

RUN mkdir -p ./bot/builtin-bot/build/decompressed && \
    tar -xf ./bot/builtin-bot/build/distributions/worker.tar -C ./cloud/worker/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev

WORKDIR /app

COPY --from=builder /app/bot/builtin-bot/build/decompressed/worker .
#if COPY --from=builder /app/deploy ./deploy
# 使用koyeb 需要把args 变成env 后文件导入
#if COPY --from=builder /app/build/envs/*.env .

ENTRYPOINT ["sh", "./bin/builtin-bot"]
# ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]

