#!/bin/sh
set -eu
script_directory=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if [ -z "${PYTHON_COMMAND:-}" ]; then
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) PYTHON_COMMAND=python ;;
    *) PYTHON_COMMAND=python3 ;;
  esac
fi
exec "$PYTHON_COMMAND" "$script_directory/run-shell-tests.py" "$@"
