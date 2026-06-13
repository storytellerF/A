#!/bin/sh

set -e

APP_DIR=${APP_DIR:-/app}
CLI_PATH=${CLI_PATH:-"$APP_DIR/bin/cli"}
PRESET_DATA_PATH=${PRESET_DATA_PATH:-"$APP_DIR/deploy/preset_data"}
FLUSH_DATABASE_SCRIPT=${FLUSH_DATABASE_SCRIPT:-"$APP_DIR/scripts/tool_scripts/flush-database.sh"}
CLI_READY_PORT=${CLI_READY_PORT:-8081}

is_enabled() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

log() {
  printf '[cli-entrypoint] %s\n' "$*"
}

if [ ! -f "$CLI_PATH" ]; then
  log "cli executable not found: $CLI_PATH"
  exit 1
fi

if [ "$#" -gt 0 ]; then
  exec sh "$CLI_PATH" "$@"
fi

if is_enabled "${CLI_INIT_ENABLE:-$INIT_ENABLE}"; then
  log "CLI_INIT_ENABLE is true; loading preset data before ready."

  if [ ! -f "$FLUSH_DATABASE_SCRIPT" ]; then
    log "flush database script not found: $FLUSH_DATABASE_SCRIPT"
    exit 1
  fi

  if [ ! -d "$PRESET_DATA_PATH" ]; then
    log "preset data directory not found: $PRESET_DATA_PATH"
    exit 1
  fi

  sh "$FLUSH_DATABASE_SCRIPT" "$CLI_PATH" "$PRESET_DATA_PATH"
  log "preset data loaded."
else
  log "CLI_INIT_ENABLE is not true; ready without preset data load."
fi

log "ready server listening on port $CLI_READY_PORT."
while true; do
  printf 'HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK' | nc -l -p "$CLI_READY_PORT"
done
