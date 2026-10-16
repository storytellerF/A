#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$ROOT_DIR/build"
TEST_DIR="$(mktemp -d "$ROOT_DIR/build/cli-entrypoint.XXXXXX")"
trap 'rm -rf "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/bin"
touch "$TEST_DIR/cli"
cat > "$TEST_DIR/bin/nc" <<'MOCK'
#!/bin/sh
cat > "$READY_RESPONSE"
exit 37
MOCK
chmod +x "$TEST_DIR/bin/nc"
result=0
PATH="$TEST_DIR/bin:$PATH" READY_RESPONSE="$TEST_DIR/response" \
CLI_PATH="$TEST_DIR/cli" CLI_INIT_ENABLE=false LOG_PATH="$TEST_DIR/log directory" \
sh "$ROOT_DIR/scripts/docker/cli-entrypoint.sh" > "$TEST_DIR/stdout" 2> "$TEST_DIR/stderr" || result=$?
[[ "$result" == 37 ]]
grep -Fq 'OK' "$TEST_DIR/response"
grep -Fq 'ready server listening' "$TEST_DIR/stdout"
grep -Fq 'ready without preset data load' "$TEST_DIR/stdout"
cmp "$TEST_DIR/stdout" "$TEST_DIR/log directory/cli.log"
[[ ! -s "$TEST_DIR/stderr" ]]
echo 'cli-entrypoint-test: ok'
