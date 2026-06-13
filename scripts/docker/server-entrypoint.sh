#!/bin/sh

set -e

APP_DIR=${APP_DIR:-/app}
CLI_PATH=${CLI_PATH:-"$APP_DIR/bin/cli"}
SERVER_PATH=${SERVER_PATH:-"$APP_DIR/bin/server"}
PRESET_DATA_PATH=${PRESET_DATA_PATH:-"$APP_DIR/deploy/preset_data"}
FLUSH_DATABASE_SCRIPT=${FLUSH_DATABASE_SCRIPT:-"$APP_DIR/scripts/tool_scripts/flush-database.sh"}

is_enabled() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

log() {
  printf '[server-entrypoint] %s\n' "$*"
}

if is_enabled "$INIT_ENABLE"; then
  log "INIT_ENABLE is true; loading preset data before server startup."

  if [ ! -f "$CLI_PATH" ]; then
    log "cli executable not found: $CLI_PATH"
    exit 1
  fi

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
  log "INIT_ENABLE is not true; skipping preset data load."
fi

exec sh "$SERVER_PATH" "$@"
