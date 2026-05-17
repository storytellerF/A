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

index=0

for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    filename=$(basename -- "$json_file")

    file_index=$(echo "$filename" | grep -o '^[0-9]\+')

    rp=$(realpath $json_file)
    if [ "$file_index" -eq "$index" ]; then
      log "Process start $rp"
      sh "$cli_path" add "$json_file"
      log "Process done $rp"
    fi

    index=$((index + 1))
  fi
done