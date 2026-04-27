#!/bin/bash

. ./scripts/tool_scripts/terminal-log.sh

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

log "Current flavor is $FLAVOR"

SESSION_NAME="A"

# 检查是否存在，存在则删除
tmux has-session -t "$SESSION_NAME" 2>/dev/null
if [ $? -eq 0 ]; then
  log "发现旧会话 $SESSION_NAME, 正在关闭..."
  tmux kill-session -t "$SESSION_NAME"
else
  log "没有会话"
fi

log "Start service..."
tmux new-session -d -s $SESSION_NAME "./scripts/service_scripts/start-service-in-local.sh $FLAVOR; exec bash"