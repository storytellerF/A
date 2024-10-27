FROM eclipse-temurin:21 AS builder

#^3
ARG IS_PROD
ARG FLAVOR
ARG BUILD_ON_HOST
ARG BUILD_ON_DOCKER
#!3

#3

WORKDIR /app

COPY . .

#2

RUN find scripts/ -type f -name "*.sh" -exec sed -i 's/\r$//' {} + && \
    sed -i 's/\r$//' gradlew

ENV IS_LOCAL_HOST=false
ENV IS_DOCKER=true

RUN --mount=type=cache,target=/root/.gradle \
    sh scripts/build_scripts/build-all-in-flavor.sh ${FLAVOR} ${IS_PROD}

FROM eclipse-temurin:21

RUN mkdir /app

WORKDIR /app
COPY --from=builder /app/server/build/libs/*-all.jar ./ktor-server.jar
COPY --from=builder /app/cli/build/distributions/cli.tar ./cli.tar
COPY --from=builder /app/deploy ./deploy
COPY scripts/tool_scripts/flush-database-singleton.sh ./scripts/tool_scripts/flush-database-singleton.sh
RUN tar -xf ./cli.tar

ENTRYPOINT ["java","-jar","./ktor-server.jar"]
