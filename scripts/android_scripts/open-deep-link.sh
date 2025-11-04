#!/bin/sh
# 参数校验
if [ $# -lt 3 ]; then
  echo "❌ 用法错误："
  echo "   ./deeplink.sh <scheme> <host> <path>"
  echo "   示例: ./deeplink.sh dev.win-debug localhost /room/100"
  exit 1
fi

SCHEME=$1
HOST=$2
LINK_PATH=$3

# 构造完整 URL
URI="a-${SCHEME}://${HOST}${LINK_PATH}"

echo "🚀 正在启动 Deep Link: ${URI}"
adb shell am start -a android.intent.action.VIEW -d "${URI}"
