FROM eclipse-temurin:21-alpine AS runtime

ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" app && \
    adduser -S -D -h /home/app -u "$APP_UID" -G app app
ENV HOME=/home/app
WORKDIR /app

FROM runtime AS filesystem-service
COPY --chown=app:app deploy/build/filesystem-service.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/filesystem-service"]

FROM runtime AS lucene-service
COPY --chown=app:app deploy/build/lucene-service.tar /tmp/service.tar
RUN tar -xf /tmp/service.tar --strip-components=1 -C /app && rm /tmp/service.tar
USER app:app
ENTRYPOINT ["sh", "./bin/lucene-service"]
