FROM openjdk:17
EXPOSE 8080
RUN mkdir /app
COPY ./*-all.jar /app/ktor-server.jar
ENTRYPOINT ["java","-jar","/app/ktor-server.jar"]