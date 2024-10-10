FROM eclipse-temurin:17 AS builder

ARG IS_PROD
ARG FLAVOR
ARG BUILD_ON_HOST

WORKDIR /app

COPY . .

RUN sed -i 's/\r$//' scripts/build-server.sh
RUN sed -i 's/\r$//' scripts/build-cli.sh
RUN sed -i 's/\r$//' gradlew

RUN sed -i "s/buildkonfig.flavor=dev/buildkonfig.flavor=${FLAVOR}/" gradle.properties
RUN sed -i "s/server.prod=false/server.prod=${IS_PROD}/" gradle.properties

ENV IS_HOST=false

RUN --mount=type=cache,target=/root/.gradle \
    sh build-server-on-condition.sh

FROM eclipse-temurin:17

RUN mkdir /app

WORKDIR /app
COPY --from=builder /app/server/build/libs/*-all.jar ./ktor-server.jar
COPY --from=builder /app/cli/build/distributions/cli.tar ./cli.tar
COPY --from=builder /app/deploy/pre_set_data ./pre_set_data
COPY ./scripts/flush-database-singleton.sh .
RUN tar -xf ./cli.tar

ENTRYPOINT ["java","-jar","./ktor-server.jar"]
