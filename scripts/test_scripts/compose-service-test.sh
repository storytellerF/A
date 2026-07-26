#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
  rm -f deploy/compose-test-*.env
}
trap cleanup EXIT

MOCK_BIN="$TMP_DIR/bin"
mkdir -p "$MOCK_BIN"

cat > "$MOCK_BIN/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$MOCK_GRADLE_ARGS"
exit "${MOCK_GRADLE_EXIT:-0}"
EOF
chmod +x "$MOCK_BIN/gradlew"

cat > "$MOCK_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$MOCK_DOCKER_ARGS"

for arg in "$@"; do
  case "$arg" in
    *docker-compose.generated-patch.yml)
      cp "$arg" "$MOCK_GENERATED_OUT"
      ;;
  esac
done
EOF
chmod +x "$MOCK_BIN/docker"

PATH="$MOCK_BIN:$PATH"
export PATH

fail() {
  echo "compose-service-test: $*" >&2
  exit 1
}

assert_contains() {
  local file=$1
  local pattern=$2
  if ! grep -Fq -- "$pattern" "$file"; then
    echo "Expected to find: $pattern" >&2
    echo "--- $file ---" >&2
    cat "$file" >&2
    fail "missing expected content"
  fi
}

assert_not_contains() {
  local file=$1
  local pattern=$2
  if grep -Fq -- "$pattern" "$file"; then
    echo "Did not expect to find: $pattern" >&2
    echo "--- $file ---" >&2
    cat "$file" >&2
    fail "unexpected content"
  fi
}

run_compose_service() {
  local flavor=$1
  local build_on=${2:-}
  local args_out="$TMP_DIR/$flavor.args"
  local generated_out="$TMP_DIR/$flavor.generated.yml"
  local gradle_out="$TMP_DIR/$flavor.gradle.args"

  : > "$args_out"
  : > "$generated_out"
  export MOCK_DOCKER_ARGS="$args_out"
  export MOCK_GENERATED_OUT="$generated_out"
  export MOCK_GRADLE_ARGS="$gradle_out"
  : > "$gradle_out"

  if [ -n "$build_on" ]; then
    BUILD_ON="$build_on" GRADLEW=gradlew BUILD_SERVER_SCRIPT=true BUILD_WS_SCRIPT=true BUILD_CLI_SCRIPT=true ./scripts/service_scripts/compose-service.sh "$flavor" false config > "$TMP_DIR/$flavor.stdout"
  else
    GRADLEW=gradlew BUILD_SERVER_SCRIPT=true BUILD_WS_SCRIPT=true BUILD_CLI_SCRIPT=true ./scripts/service_scripts/compose-service.sh "$flavor" false config > "$TMP_DIR/$flavor.stdout"
  fi

  [ -s "$generated_out" ] || fail "generated compose override was not captured for $flavor"
}

write_env() {
  local flavor=$1
  local profiles=$2
  local env_file="deploy/$flavor.env"

  cat > "$env_file" <<EOF
BUILD_TYPE=dev
FLAVOR=$flavor
BUILD_ON=docker
COMPOSE_PROJECT_NAME=a-$flavor
COMPOSE_FILE_LIST=$profiles
SERVER_PORT=8811
SERVER_EXPOSE_PORT=8811
CLI_INIT_ENABLE=false
CLI_READY_PORT=8081
PRESET_PATH=../dev-data
SERVER_URL=http://localhost:8811
WS_SERVER_URL=ws://localhost:8813
EOF
}

dependency_flavor="compose-test-deps"
write_env "$dependency_flavor" "pg,elastic,minio,certs_bind"
run_compose_service "$dependency_flavor"
assert_contains "$TMP_DIR/$dependency_flavor.generated.yml" "services: {}"
assert_not_contains "$TMP_DIR/$dependency_flavor.args" "docker-compose._"
assert_not_contains "$TMP_DIR/$dependency_flavor.args" "docker-compose.cli.yml"
rm -f "deploy/$dependency_flavor.env"

server_flavor="compose-test-server"
write_env "$server_flavor" "pg,elastic,minio,certs_bind,cli,server"
run_compose_service "$server_flavor" "local"
assert_contains "$TMP_DIR/$server_flavor.args" "docker-compose.cli.yml"
assert_contains "$TMP_DIR/$server_flavor.args" "docker-compose.server.yml"
assert_not_contains "$TMP_DIR/$server_flavor.args" "docker-compose._"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "  cli:"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "  server:"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "      cli:"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "      pg:"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "      minio:"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "      es01:"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "        condition: service_healthy"
assert_contains "$TMP_DIR/$server_flavor.generated.yml" "      - certs:/app/deploy/es_ca"
rm -f "deploy/$server_flavor.env"

server_env_build_on_flavor="compose-test-server-env-build-on"
write_env "$server_env_build_on_flavor" "server"
run_compose_service "$server_env_build_on_flavor"
assert_contains "$TMP_DIR/$server_env_build_on_flavor.args" "docker-compose.server.yml"
rm -f "deploy/$server_env_build_on_flavor.env"

bunker_flavor="compose-test-bunker"
write_env "$bunker_flavor" "pg,elastic,minio,certs_bind,grafana,dicebear,etcd,cli,server,worker,bunker"
run_compose_service "$bunker_flavor" "local"
assert_not_contains "$TMP_DIR/$bunker_flavor.args" "docker-compose._bunker.yml"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "networks:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  bw-services:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "    external: true"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "    name: bw-services"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  server:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  worker:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  cli:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  pg:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  minio:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  grafana:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  prometheus:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  loki:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  promtail:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  setup:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  es01:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  kibana:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  dicebear:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  etcd:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "      - bw-services"
rm -f "deploy/$bunker_flavor.env"

app_flavor="compose-test-app"
write_env "$app_flavor" "pg,server,app"
run_compose_service "$app_flavor"
assert_contains "$TMP_DIR/$app_flavor.args" "docker-compose.app.yml"
if [ -s "$TMP_DIR/$app_flavor.gradle.args" ]; then
  fail "compose-service must not build the Wasm distributions itself"
fi

app_start_args="$TMP_DIR/app-start.args"
app_start_generated="$TMP_DIR/app-start.generated.yml"
app_start_gradle_args="$TMP_DIR/app-start.gradle.args"
: > "$app_start_args"
: > "$app_start_generated"
: > "$app_start_gradle_args"
MOCK_DOCKER_ARGS="$app_start_args" \
MOCK_GENERATED_OUT="$app_start_generated" \
MOCK_GRADLE_ARGS="$app_start_gradle_args" \
GRADLEW=gradlew \
BUILD_CLOUD_SCRIPT=true \
./scripts/service_scripts/start-service-in-local.sh "$app_flavor" > "$TMP_DIR/app-start.stdout"
assert_contains "$app_start_args" "docker-compose.app.yml"
assert_contains "$app_start_gradle_args" ":app:webApp:wasmJsBrowserDistribution"
assert_contains "$app_start_gradle_args" ":panel:webApp:wasmJsBrowserDistribution"
assert_contains "$app_start_gradle_args" "-Ptarget.wasm=true"
assert_contains "$app_start_gradle_args" "-Pserver.flavor=$app_flavor"
assert_contains "$app_start_gradle_args" "-Pserver.buildType=dev"
rm -f "deploy/$app_flavor.env"

no_app_flavor="compose-test-no-app"
write_env "$no_app_flavor" "pg,server"
no_app_start_args="$TMP_DIR/no-app-start.args"
no_app_start_generated="$TMP_DIR/no-app-start.generated.yml"
no_app_start_gradle_args="$TMP_DIR/no-app-start.gradle.args"
: > "$no_app_start_args"
: > "$no_app_start_generated"
: > "$no_app_start_gradle_args"
MOCK_DOCKER_ARGS="$no_app_start_args" \
MOCK_GENERATED_OUT="$no_app_start_generated" \
MOCK_GRADLE_ARGS="$no_app_start_gradle_args" \
GRADLEW=gradlew \
BUILD_CLOUD_SCRIPT=true \
./scripts/service_scripts/start-service-in-local.sh "$no_app_flavor" > "$TMP_DIR/no-app-start.stdout"
if [ -s "$no_app_start_gradle_args" ]; then
  fail "start-service-in-local must skip Wasm builds without the app profile"
fi
rm -f "deploy/$no_app_flavor.env"

if find deploy/docker-compose -maxdepth 1 \( -name 'docker-compose._*.yml' -o -name 'docker-compose.generated.??????.yml' \) | grep -q .; then
  find deploy/docker-compose -maxdepth 1 \( -name 'docker-compose._*.yml' -o -name 'docker-compose.generated.??????.yml' \)
  fail "stale generated or underscore compose files found"
fi

echo "compose-service-test: ok"
