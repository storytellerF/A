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
printf 'APP_ENV_FILE=%s\n' "${APP_ENV_FILE:-}" >> "$MOCK_DOCKER_ARGS"
if [[ "${EXPECT_DOCKER_BUILD:-false}" == "true" && "${BUILD_ON:-}" != "docker" ]]; then
  echo "Deployment must build inside Docker" >&2
  exit 1
fi

for arg in "$@"; do
  case "$arg" in
    *docker-compose.generated-patch.yml)
      cp "$arg" "$MOCK_GENERATED_OUT"
      ;;
  esac
done
exit "${MOCK_DOCKER_EXIT:-0}"
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

for profiles in "filesystem,lucene,server,worker" "pg,filesystem,lucene,server,worker"; do
  rpc_flavor="compose-test-rpc-no-cli"
  write_env "$rpc_flavor" "$profiles"
  run_compose_service "$rpc_flavor"
  for service in server worker ws; do
    service_section=$(sed -n "/^  $service:/,/^  [a-z]/p" "$TMP_DIR/$rpc_flavor.generated.yml")
    grep -Fq "      FILESYSTEM_RPC_URL: ws://filesystem:8820/rpc" <<< "$service_section" || fail "$service misses filesystem environment"
    grep -Fq "      LUCENE_RPC_URL: ws://lucene:8821/rpc" <<< "$service_section" || fail "$service misses lucene environment"
    for dependency in filesystem lucene; do
      grep -Fq "      $dependency:" <<< "$service_section" || fail "$service misses $dependency dependency"
    done
  done
  rm -f "deploy/$rpc_flavor.env"
done

for backend in filesystem lucene; do
  assert_not_contains "deploy/docker-compose/docker-compose.$backend.yml" "  cli:"
  assert_not_contains "deploy/docker-compose/docker-compose.$backend.yml" "  server:"
  assert_not_contains "deploy/docker-compose/docker-compose.$backend.yml" "  worker:"
  assert_not_contains "deploy/docker-compose/docker-compose.$backend.yml" "  ws:"
done
assert_contains "deploy/docker-compose/docker-compose.server.yml" "      SERVER_PORT: 80"
assert_contains "deploy/docker-compose/docker-compose.server.yml" "      WS_SERVER_PORT: 80"
assert_contains "minio.Dockerfile" "apk add --no-cache ca-certificates curl"

server_env_build_on_flavor="compose-test-server-env-build-on"
write_env "$server_env_build_on_flavor" "server"
run_compose_service "$server_env_build_on_flavor"
assert_contains "$TMP_DIR/$server_env_build_on_flavor.args" "docker-compose.server.yml"
rm -f "deploy/$server_env_build_on_flavor.env"

bunker_flavor="compose-test-bunker"
write_env "$bunker_flavor" "pg,elastic,minio,certs_bind,grafana,dicebear,etcd,cli,server,worker,bunker"
BUNKER_TEST_ENV="$TMP_DIR/bunker.env"
cp deploy/bunker.env.example "$BUNKER_TEST_ENV"
BUNKER_ENV_FILE="$BUNKER_TEST_ENV" run_compose_service "$bunker_flavor" "local"
assert_not_contains "$TMP_DIR/$bunker_flavor.args" "--env-file $BUNKER_TEST_ENV"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "    env_file:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "$BUNKER_TEST_ENV"
assert_contains "$TMP_DIR/$bunker_flavor.args" "docker-compose.bunker.yml"
assert_contains "deploy/bunker.env.example" 'adminer.example.com_REVERSE_PROXY_URL=~ ^/'
assert_not_contains "deploy/docker-compose/docker-compose.bunker.yml" '${BUNKER_'
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "networks:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  bw-services:"
assert_not_contains "$TMP_DIR/$bunker_flavor.generated.yml" "    external: true"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "    name: bw-services"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" "  server:"
assert_contains "$TMP_DIR/$bunker_flavor.generated.yml" $'  signin-redis:\n    networks:\n      - bw-services'
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
assert_contains "deploy/docker-compose/docker-compose.app.yml" "dockerfile: wasm.Dockerfile"
assert_contains "deploy/docker-compose/docker-compose.app.yml" "target: app-wasm"
assert_contains "deploy/docker-compose/docker-compose.app.yml" "target: panel-wasm"
assert_contains "deploy/docker-compose/docker-compose.app.yml" 'SERVER_URL: ${SERVER_URL}'
assert_contains "deploy/docker-compose/docker-compose.app.yml" 'WS_SERVER_URL: ${WS_SERVER_URL}'
assert_not_contains "deploy/docker-compose/docker-compose.app.yml" "flavor_env"
assert_not_contains "deploy/docker-compose/docker-compose.app.yml" "productionExecutable:/usr/share/nginx/html"
assert_contains "deploy/docker-compose/docker-compose.server.yml" "target: server"
assert_contains "deploy/docker-compose/docker-compose.server.yml" "target: ws"
assert_contains "deploy/docker-compose/docker-compose.worker.yml" "target: worker"
assert_contains "deploy/docker-compose/docker-compose.cli.yml" "target: cli"
assert_contains "Dockerfile" "AS server"
assert_contains "Dockerfile" "AS worker"
assert_contains "Dockerfile" "AS ws"
assert_contains "Dockerfile" "AS cli"
assert_contains "gradle.properties" "org.gradle.parallel=true"
assert_contains "gradle.properties" "detekt.use.worker.api = false"
assert_not_contains "Dockerfile" "org.gradle.parallel=false"
assert_not_contains "wasm.Dockerfile" "org.gradle.parallel=false"
assert_not_contains "wasm.Dockerfile" "--no-parallel"
assert_not_contains "wasm.Dockerfile" "flavor_env"
assert_not_contains "wasm.Dockerfile" 'deploy/${FLAVOR}.env'
assert_contains "deploy/docker-compose/docker-compose.app.yml" './app-wasm.nginx.conf:/etc/nginx/conf.d/default.conf:ro'
assert_not_contains "wasm.Dockerfile" 'COPY deploy/docker-compose/app-wasm.nginx.conf'
assert_not_contains "scripts/build_scripts/build-service-images.sh" "worker.Dockerfile"
assert_not_contains "scripts/build_scripts/build-service-images.sh" "ws.Dockerfile"
assert_not_contains "scripts/build_scripts/build-service-images.sh" "cli.Dockerfile"

local_backend_flavor="compose-test-local-backends"
write_env "$local_backend_flavor" "pg,filesystem,lucene,cli,server,worker"
run_compose_service "$local_backend_flavor"
assert_contains "$TMP_DIR/$local_backend_flavor.args" "docker-compose.filesystem.yml"
assert_contains "$TMP_DIR/$local_backend_flavor.args" "docker-compose.lucene.yml"
assert_contains "$TMP_DIR/$local_backend_flavor.generated.yml" "      filesystem:"
assert_contains "$TMP_DIR/$local_backend_flavor.generated.yml" "      lucene:"
assert_not_contains "$TMP_DIR/$local_backend_flavor.generated.yml" "../a_file_data:/app/deploy/a_file_data"
assert_not_contains "$TMP_DIR/$local_backend_flavor.generated.yml" "../lucene_data:/app/deploy/lucene_data"
assert_contains "Dockerfile" "AS filesystem-service"
assert_contains "Dockerfile" "AS lucene-service"
assert_contains "deploy/docker-compose/docker-compose.minio.yml" "dockerfile: minio.Dockerfile"

docker_start_args="$TMP_DIR/docker-start.args"
: > "$docker_start_args"
MOCK_DOCKER_ARGS="$docker_start_args" \
MOCK_GENERATED_OUT="$TMP_DIR/docker-start.generated.yml" \
MOCK_GRADLE_ARGS="$TMP_DIR/docker-start.gradle.args" \
EXPECT_DOCKER_BUILD=true \
./scripts/service_scripts/start-service-in-docker.sh "$local_backend_flavor" > "$TMP_DIR/docker-start.stdout"
mapfile -t docker_builds < <(grep ' build ' "$docker_start_args")
[[ ${#docker_builds[@]} -eq 1 ]] || fail "expected one parallel backend build submission"
[[ "${docker_builds[0]}" == *"build filesystem lucene cli worker server ws" ]] || fail "backend targets missing"
assert_contains "$docker_start_args" "up -d --no-build"
assert_not_contains "$docker_start_args" "up -d --build"
rm -f "deploy/$local_backend_flavor.env"

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
EXPECT_DOCKER_BUILD=true \
BUILD_ON=local \
./scripts/service_scripts/start-service-in-docker.sh "$app_flavor" > "$TMP_DIR/app-start.stdout"
assert_contains "$app_start_args" "docker-compose.app.yml"
mapfile -t app_builds < <(grep ' build ' "$app_start_args")
[[ ${#app_builds[@]} -eq 1 ]] || fail "expected one parallel backend/Wasm build submission"
[[ "${app_builds[0]}" == *"build server ws app-wasm panel-wasm" ]] || fail "app targets missing"
assert_contains "$app_start_args" "up -d --no-build"
if [ -s "$app_start_gradle_args" ]; then
  fail "Docker deployment must not invoke host Gradle"
fi
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
EXPECT_DOCKER_BUILD=true \
./scripts/service_scripts/start-service-in-docker.sh "$no_app_flavor" > "$TMP_DIR/no-app-start.stdout"
assert_not_contains "$no_app_start_args" "build app-wasm"
assert_not_contains "$no_app_start_args" "build panel-wasm"
assert_contains "$no_app_start_args" "up -d --no-build"
if [ -s "$no_app_start_gradle_args" ]; then
  fail "Docker deployment must not invoke host Gradle without the app profile"
fi
rm -f "deploy/$no_app_flavor.env"

external_flavor="compose-test-external"
write_env "$external_flavor" "filesystem,lucene,cli,server,worker,app,bunker"
external_directory="$TMP_DIR/external config"
mkdir -p "$external_directory"
external_env="$external_directory/alpha.env"
cp "deploy/$external_flavor.env" "$external_env"
rm -f "deploy/$external_flavor.env"
printf '\nBUNKER_ENV_FILE=bunker.env\n' >> "$external_env"
cp deploy/bunker.env.example "$external_directory/bunker.env"
external_args="$TMP_DIR/external.args"
external_generated="$TMP_DIR/external.generated.yml"
export MOCK_DOCKER_ARGS="$external_args"
export MOCK_GENERATED_OUT="$external_generated"
export MOCK_GRADLE_ARGS="$TMP_DIR/external.gradle.args"
: > "$external_args"
: > "$MOCK_GRADLE_ARGS"
./scripts/service_scripts/compose-service.sh "$external_env" false config > "$TMP_DIR/external.stdout"
assert_contains "$external_args" "--env-file $external_env"
assert_contains "$external_args" "APP_ENV_FILE=$external_env"
assert_not_contains "$external_args" "--env-file $external_directory/bunker.env"
assert_contains "$external_generated" "path: '$external_directory/bunker.env'"
(cd "$external_directory" && "$ROOT_DIR/scripts/service_scripts/compose-service.sh" alpha.env false config) > "$TMP_DIR/external-relative.stdout"
assert_contains "$external_args" "APP_ENV_FILE=$external_env"
assert_not_contains "$external_args" "exec -T bunkerweb nginx -s reload"
EXPECT_DOCKER_BUILD=true ./scripts/service_scripts/start-service-in-docker.sh "$external_env" > "$TMP_DIR/external-start.stdout"
assert_contains "$external_args" "build filesystem lucene cli worker server ws app-wasm panel-wasm"
assert_contains "$external_args" "app-wasm panel-wasm"
assert_contains "$external_args" "up -d --no-build"
assert_contains "$external_args" "exec -T bunkerweb nginx -s reload"
if ./scripts/service_scripts/compose-service.sh "$external_directory/missing.env" false config > "$TMP_DIR/missing.stdout" 2>&1; then
  fail "missing explicit environment path must fail"
fi

if find deploy/docker-compose -maxdepth 1 \( -name 'docker-compose._*.yml' -o -name 'docker-compose.generated.??????.yml' \) | grep -q .; then
  find deploy/docker-compose -maxdepth 1 \( -name 'docker-compose._*.yml' -o -name 'docker-compose.generated.??????.yml' \)
  fail "stale generated or underscore compose files found"
fi

failed_flavor="compose-test-failed-build"
write_env "$failed_flavor" "pg,server"
failed_args="$TMP_DIR/failed-build.args"
: > "$failed_args"
failed_exit=0
MOCK_DOCKER_ARGS="$failed_args" \
MOCK_GENERATED_OUT="$TMP_DIR/failed-build.generated.yml" \
MOCK_DOCKER_EXIT=7 \
./scripts/service_scripts/start-service-in-docker.sh "$failed_flavor" > "$TMP_DIR/failed-build.stdout" 2>&1 || failed_exit=$?
[[ $failed_exit == 7 ]] || fail "image-build failure status must be preserved"
assert_not_contains "$failed_args" "up -d"
rm -f "deploy/$failed_flavor.env"

echo "compose-service-test: ok"
