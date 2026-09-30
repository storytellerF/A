# syntax=docker/dockerfile:1.7

FROM golang:1.24-alpine AS builder

ARG MINIO_VERSION=RELEASE.2025-04-22T22-12-26Z
RUN apk add --no-cache git && \
    GOTOOLCHAIN=auto go install "github.com/minio/minio@${MINIO_VERSION}"

FROM alpine:3.22

RUN apk add --no-cache ca-certificates && \
    addgroup -S -g 1000 minio && \
    adduser -S -D -h /home/minio -u 1000 -G minio minio
COPY --from=builder /go/bin/minio /usr/local/bin/minio

USER minio:minio
VOLUME ["/data"]
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
