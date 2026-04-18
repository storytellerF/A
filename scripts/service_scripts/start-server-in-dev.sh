#!/usr/bin/env bash

set -e

. ./scripts/tool_scripts/export-env.sh

./gradlew cloud:server:run