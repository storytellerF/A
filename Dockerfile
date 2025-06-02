FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl unzip

#^1
ARG BUILD_TYPE
ARG FLAVOR
ARG BUILD_ON
#!1

#1

WORKDIR /app

COPY . .

#2

RUN find scripts/ -type f \( -name "*.sh" -o -name "*.js" \) -exec sed -i 's/\r$//' {} + && \
    sed -i 's/\r$//' gradlew

RUN ./scripts/download_scripts/download-data.sh

ENV HOST_TYPE=docker

RUN --mount=type=cache,target=/root/.gradle \
    ./scripts/build_scripts/build-server-on-condition.sh ${FLAVOR} ${BUILD_TYPE} ${BUILD_ON}

RUN mkdir -p ./cli/build/decompressed && tar -xf ./cli/build/distributions/cli.tar -C ./cli/build/decompressed

RUN mkdir -p ./build/envs

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev

RUN mkdir /app

WORKDIR /app
COPY --from=builder /app/server/build/libs/*-all.jar ./lib/ktor-server.jar
COPY --from=builder /app/cli/build/decompressed/cli .
#if COPY --from=builder /app/deploy ./deploy
# 使用koyeb 需要把args 变成env 后文件导入
COPY --from=builder /app/build/envs/*.env .
COPY scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh

ENTRYPOINT ["java","-jar","./lib/ktor-server.jar"]
