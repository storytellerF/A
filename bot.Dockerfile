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
    tar -xf ./bot/builtin-bot/build/distributions/builtin-bot.tar -C ./bot/builtin-bot/build/decompressed

FROM eclipse-temurin:21-alpine

WORKDIR /app

COPY --from=builder /app/bot/builtin-bot/build/decompressed/builtin-bot .

ENTRYPOINT ["sh", "./bin/builtin-bot"]
# ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]

