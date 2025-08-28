FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash curl unzip

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

RUN find scripts/ -type f \( -name "*.sh" -o -name "*.js" \) -exec sed -i 's/\r$//' {} + && \
    sed -i 's/\r$//' gradlew

RUN ./scripts/download_scripts/download-preset-data.sh

RUN --mount=type=cache,target=/root/.gradle ./scripts/build_scripts/build-cloud-on-condition.sh ${FLAVOR} ${BUILD_TYPE} ${BUILD_ON}

RUN mkdir -p ./cloud/cli/build/decompressed && tar -xf ./deploy/build/cli.tar -C ./cloud/cli/build/decompressed

FROM eclipse-temurin:21-alpine

RUN apk add libavif-dev

WORKDIR /app

COPY --from=builder /app/deploy/build/*-all.jar ./lib/ktor-server.jar
COPY --from=builder /app/cloud/cli/build/decompressed/cli .
#if($koyeb) COPY --from=builder /app/deploy ./deploy
# 使用koyeb 需要把args 变成env 后文件导入
#if($koyeb) COPY --from=builder /app/build/envs/*.env .
COPY --from=builder /app/scripts/tool_scripts/flush-database.sh ./scripts/tool_scripts/flush-database.sh

ENTRYPOINT ["java","-jar","./lib/ktor-server.jar"]
