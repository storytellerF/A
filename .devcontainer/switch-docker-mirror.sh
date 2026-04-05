#!/bin/bash
use_cn_mirror=$1
if [ "$use_cn_mirror" != "true" ]; then
    echo "Using default Docker registry mirrors."
    exit 0
fi
echo "Using China Docker registry mirrors."
SOURCE_REGISTRY='"https://docker.1ms.run","https://docker.1panel.live","https://docker.m.daocloud.io"'

mkdir -p /etc/docker
[ -s "/etc/docker/daemon.json" ] || echo "{}" >/etc/docker/daemon.json
jq '.["registry-mirrors"] = ['"${SOURCE_REGISTRY}"']' /etc/docker/daemon.json >/etc/docker/daemon.json.tmp \
    && mv /etc/docker/daemon.json.tmp /etc/docker/daemon.json