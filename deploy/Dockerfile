FROM openjdk:17

RUN microdnf install findutils

RUN mkdir /app

WORKDIR /app
COPY ./build/*-all.jar ./ktor-server.jar
COPY ./build/cli.tar ./cli.tar
COPY flush-database-singleton.sh .
COPY ./pre_set_data ./data
RUN tar -xf ./cli.tar

ENTRYPOINT ["java","-jar","./ktor-server.jar"]