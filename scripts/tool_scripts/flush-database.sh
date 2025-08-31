#!/bin/sh
set -e
cli_path=$1
base=$2

if [ -z "$base" ] || [ -z "$cli_path" ]; then
  echo "Error: base and cli_path parameter is not specified."
  exit 1
fi

log() {
  # Linux 默认支持 %N
  if date +"%N" >/dev/null 2>&1; then
    ts=$(date +"%Y-%m-%d %H:%M:%S.%3N")
  else
    # macOS 没有 %N，需要用 gdate
    if command -v gdate >/dev/null 2>&1; then
      ts=$(gdate +"%Y-%m-%d %H:%M:%S.%3N")
    else
      ts=$(date +"%Y-%m-%d %H:%M:%S") # 退化为秒级
    fi
  fi
  echo "$ts [shell] INFO SHELL - $*"
}

sh "$cli_path" clean
log "clean result is $?"

sh "$cli_path" init
log "init result is $?"

index=0

for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    filename=$(basename -- "$json_file")

    file_index=$(echo "$filename" | grep -o '^[0-9]\+')

    if [ "$file_index" -eq "$index" ]; then
      log "Process start $json_file"
      sh "$cli_path" add "$json_file"
      log "Process done $json_file, result is $?"
    fi

    index=$((index + 1))
  fi
done