#!/bin/bash
set -e
base=$1
if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi
FLAVOR=dev
# 检测操作系统
if [[ "$(uname -s)" =~ MINGW|CYGWIN|MSYS ]]; then
  FLAVOR=dev.win
fi


cli_path=build/install/cli/bin/cli

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" dev

# 执行 Gradle 构建并捕获退出码
if ! ./gradlew cloud:cli:installDist; then
  echo "Gradle build failed"
  ./scripts/tool_scripts/show-notification.sh "构建失败" "cloud:cli:installDist 构建过程中出现错误" "false"
  exit 1
fi

source ./scripts/tool_scripts/set-log-path.sh
cd cloud/cli

# 执行数据库刷新并捕获退出码
if ../../scripts/tool_scripts/flush-database.sh $cli_path "../../$base"; then
  echo "Database flush completed successfully"
  ../../scripts/tool_scripts/show-notification.sh "数据库刷新成功" "开发环境数据库已成功刷新" "true"
  exit 0
else
  echo "Database flush failed"
  ../../scripts/tool_scripts/show-notification.sh "数据库刷新失败" "开发环境数据库刷新过程中出现错误" "false"
  exit 1
fi
