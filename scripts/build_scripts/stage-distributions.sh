#!/bin/sh
set -eu
for archive in deploy/build/*.tar; do
    [ -f "$archive" ] || continue
    service=${archive##*/}
    service=${service%.tar}
    mkdir -p "deploy/staged/$service"
    tar -xf "$archive" --strip-components=1 -C "deploy/staged/$service"
done
