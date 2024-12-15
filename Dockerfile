FROM eclipse-temurin:21-alpine AS builder

RUN apk add bash

#^1
ARG IS_PROD
ARG FLAVOR
ARG BUILD_ON
#!1

#1

WORKDIR /app

COPY . .

#2

RUN find scripts/ -type f \( -name "*.sh" -o -name "*.js" \) -exec sed -i 's/\r$//' {} + && \
    sed -i 's/\r$//' gradlew

RUN ./scripts/download_scripts/download_data.sh

ENV HOST_TYPE=docker

RUN --mount=type=cache,target=/root/.gradle \
    ./scripts/build_scripts/build-all-in-flavor.sh ${FLAVOR} ${IS_PROD}

RUN mkdir -p ./cli/build/decompressed && tar -xf ./cli/build/distributions/cli.tar -C ./cli/build/decompressed

FROM eclipse-temurin:21-alpine

RUN mkdir /app

WORKDIR /app
COPY --from=builder /app/server/build/libs/*-all.jar ./ktor-server.jar
COPY --from=builder /app/cli/build/decompressed/cli .
COPY --from=builder /app/deploy ./deploy
COPY scripts/tool_scripts/flush-database-singleton.sh ./scripts/tool_scripts/flush-database-singleton.sh

ENTRYPOINT ["java","-jar","./ktor-server.jar"]
