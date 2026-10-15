# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM golang:1.24-alpine@sha256:8bee1901f1e530bfb4a7850aa7a479d17ae3a18beb6e09064ed54cfd245b7191 AS builder

ARG MINIO_VERSION=RELEASE.2025-04-22T22-12-26Z
RUN apk add --no-cache git && \
    GOTOOLCHAIN=auto go install "github.com/minio/minio@${MINIO_VERSION}"

FROM alpine:3.22@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8

RUN apk add --no-cache ca-certificates curl && \
    addgroup -S -g 1000 minio && \
    adduser -S -D -h /home/minio -u 1000 -G minio minio && \
    mkdir -p /data && chown minio:minio /data
COPY --from=builder /go/bin/minio /usr/local/bin/minio

USER minio:minio
VOLUME ["/data"]
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
