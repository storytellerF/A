#!/bin/bash
# Custom entrypoint script for executing various maintenance tasks
# Task execution status is tracked in ~/.a/cmd

set -e
# check arch select SYS_IMG_PKG
if [ "$(uname -m)" = "x86_64" ]; then
    export SYS_IMG_PKG="system-images;android-36;google_apis;x86_64"
else
    export SYS_IMG_PKG="system-images;android-36;google_apis;arm64"
fi

./bin/action-after-create.sh

./bin/entrypoint.sh