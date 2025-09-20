#!/bin/bash

# 定义颜色
RED='\033[0;31m'      # 红色
GREEN='\033[0;32m'    # 绿色
YELLOW='\033[1;33m'   # 黄色
CYAN='\033[0;36m'     # 青色
NC='\033[0m'          # 无色 (reset)

# 检查是否提供了端口参数
if [ -z "$1" ]; then
  echo -e "${RED}❌ Please provide a port number.${NC}"
  exit 1
fi

PORT=$1

# 遍历所有已连接的设备并设置端口转发
for device in $(adb devices | grep -w 'device' | cut -f1); do
  echo -e "${CYAN}🔗 Setting up port forwarding for device ${YELLOW}$device${NC} on port ${GREEN}$PORT${NC}"
  adb -s "$device" reverse tcp:"$PORT" tcp:"$PORT"
done

echo -e "${GREEN}✅ Port forwarding setup complete on port $PORT!${NC}"
