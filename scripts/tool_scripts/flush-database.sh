#!/bin/sh

# 获取当前脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/terminal-log.sh"

set -e
cli_path=$1
base=$2

if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi

if [ -z "$cli_path" ]; then
  echo "Error: cli_path parameter is not specified."
  exit 1
fi

sh "$cli_path" clean
log "clean result is $?"

sh "$cli_path" init
log "init result is $?"

tab=$(printf '\t')

# Validate every preset before importing anything so ordering is never ambiguous.
for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    filename=${json_file##*/}
    file_index=${filename%%[!0-9]*}
    if [ -z "$file_index" ]; then
      echo "Error: preset filename has no numeric index: $filename"
      exit 1
    fi
  fi
done

for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    filename=${json_file##*/}
    file_index=${filename%%[!0-9]*}
    printf '%s\t%s\n' "$file_index" "$json_file"
  fi
done | LC_ALL=C sort -t "$tab" -k1,1n -k2,2 | while IFS="$tab" read -r file_index json_file; do
  if [ -n "$json_file" ]; then
    rp=$(realpath "$json_file")
    log "Process start index=$file_index $rp"
    sh "$cli_path" add "$json_file"
    log "Process done index=$file_index $rp"
  fi
done
