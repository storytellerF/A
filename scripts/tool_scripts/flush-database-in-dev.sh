#!/bin/bash
set -e
base=$1
if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi

# 执行 Gradle 构建并捕获退出码
if ! ./gradlew cloud:cli:installDist -Pserver.flavor=dev -Pserver.buildType=dev; then
  echo "Gradle build failed"
  ./scripts/tool_scripts/show-notification.sh "构建失败" "cloud:cli:installDist 构建过程中出现错误" "false"
  exit 1
fi

. ./scripts/tool_scripts/set-log-path.sh

# 读取dev.env 文件并导出环境变量
if [ -f "deploy/dev.env" ]; then
  echo "Loading environment variables from deploy/dev.env"
  while IFS='=' read -r key value; do
    # 跳过注释和空行
    [[ "$key" =~ ^#.*$ || -z "$key" ]] && continue
    export "$key=$value"
  done < deploy/dev.env
else
  echo "Warning: dev.env file not found. Skipping environment variable setup."
fi

cd cloud/cli
cli_path=build/install/cli/bin/cli
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
