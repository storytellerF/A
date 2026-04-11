#!/bin/bash

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

# 获取当前脚本的名称
SCRIPT_NAME=$(basename "$0")
log "[$SCRIPT_NAME] log service started"
