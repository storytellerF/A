#!/usr/bin/env bash

# Resolve configuration without sourcing credentials as shell code.
SERVICE_PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"

resolve_service_env() {
  local selector=${1:-}
  local candidate
  if [ -z "$selector" ]; then
    echo "Environment name or path must be set." >&2
    return 1
  fi
  selector=${selector/#\~\//$HOME/}
  if [ -f "$selector" ]; then
    candidate=$selector
  elif [[ "$selector" != */* ]]; then
    local name=${selector%.env}
    if [ -f "$HOME/deploy/$name.env" ]; then
      candidate="$HOME/deploy/$name.env"
    else
      candidate="$SERVICE_PROJECT_ROOT/deploy/$name.env"
    fi
  else
    candidate=$selector
  fi
  if [ ! -f "$candidate" ]; then
    echo "Environment file does not exist: $candidate" >&2
    return 1
  fi
  ENV_FILE="$(cd "$(dirname "$candidate")" && pwd -P)/$(basename "$candidate")"
  export APP_ENV_FILE="$ENV_FILE"
}

read_service_env() {
  local value
  value=$(sed -n "s/^${1}=//p" "$ENV_FILE" | tail -n 1)
  value=${value%$'\r'}
  if [[ "$value" == \"*\" ]] || [[ "$value" == \'*\' ]]; then
    value=${value:1:${#value}-2}
  fi
  printf '%s' "$value"
}

resolve_bunker_env() {
  local candidate=${BUNKER_ENV_FILE:-$(read_service_env BUNKER_ENV_FILE)}
  candidate=${candidate:-$HOME/deploy/bunker.env}
  candidate=${candidate/#\~\//$HOME/}
  if [[ "$candidate" != /* ]]; then
    candidate="$(dirname "$ENV_FILE")/$candidate"
  fi
  if [ ! -f "$candidate" ]; then
    echo "Bunker environment file does not exist: $candidate" >&2
    return 1
  fi
  BUNKER_ENV_FILE="$(cd "$(dirname "$candidate")" && pwd -P)/$(basename "$candidate")"
}
