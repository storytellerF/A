FROM ubuntu AS builder

RUN apt-get update
RUN apt-get install -y --no-install-recommends \
    openjdk-21-jdk \
    curl \
    unzip \
    jq \
    dos2unix

WORKDIR /app
COPY deploy .
ENV HOST_TYPE=local

RUN ./scripts/tool_scripts/shell-crlf.sh

RUN ./scripts/download_scripts/manual-download-docker-image.sh


RUN --mount=type=cache,target=/root/.gradle \
    ./scripts/build_scripts/build-server-on-condition.sh ${FLAVOR} ${BUILD_TYPE} ${BUILD_ON}

FROM koyeb/docker-compose

RUN apk add --no-cache git

WORKDIR /app

COPY --from=builder / .

CMD sh ./scripts/service_scripts/start-compose-in-koyeb.sh ${FLAVOR}
