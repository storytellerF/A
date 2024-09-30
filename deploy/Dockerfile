FROM openjdk:17
RUN mkdir /app

WORKDIR /app
COPY ./build/*-all.jar ./ktor-server.jar
COPY ./build/cli.tar .
RUN tar -xf ./cli.tar
ENTRYPOINT ["java","-jar","./ktor-server.jar"]