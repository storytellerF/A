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

cat > "$MOCK_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$MOCK_DOCKER_ARGS"

for arg in "$@"; do
  case "$arg" in
    *docker-compose.generated.*.yml)
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

  : > "$args_out"
  : > "$generated_out"
  export MOCK_DOCKER_ARGS="$args_out"
  export MOCK_GENERATED_OUT="$generated_out"

  if [ -n "$build_on" ]; then
    BUILD_ON="$build_on" ./scripts/service_scripts/compose-service.sh "$flavor" false config > "$TMP_DIR/$flavor.stdout"
  else
    ./scripts/service_scripts/compose-service.sh "$flavor" false config > "$TMP_DIR/$flavor.stdout"
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
BUILD_ON=
COMPOSE_PROJECT_NAME=a-$flavor
COMPOSE_FILE_LIST=$profiles
SERVER_PORT=8811
SERVER_EXPOSE_PORT=8811
CLI_INIT_ENABLE=false
CLI_READY_PORT=8081
PRESET_PATH=../dev-data
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

if find deploy/docker-compose -maxdepth 1 \( -name 'docker-compose._*.yml' -o -name 'docker-compose.generated.*.yml' \) | grep -q .; then
  find deploy/docker-compose -maxdepth 1 \( -name 'docker-compose._*.yml' -o -name 'docker-compose.generated.*.yml' \)
  fail "stale generated or underscore compose files found"
fi

echo "compose-service-test: ok"
