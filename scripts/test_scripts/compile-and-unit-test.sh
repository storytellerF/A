#!/bin/sh

echo "执行 detekt 静态分析"
if ! ./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "Detekt 失败" "代码静态分析失败！请检查代码规范问题。" "false"
    exit 1
fi

./scripts/tool_scripts/kill-port.bat 8080
./scripts/tool_scripts/kill-port.bat 9080
./scripts/tool_scripts/kill-port.bat 10080

echo "清理测试缓存"
if ! ./gradlew clean --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "清理失败" "clean 执行失败！" "false"
    exit 1
fi

echo "构建 cloud:server"
rm -rf cloud/server/build/install/server
if ! ./gradlew cloud:server:installDist --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "构建失败" "cloud:server:installDist 构建失败！请检查编译错误。" "false"
    exit 1
fi

echo "执行单元测试"
if ! ENABLE_TEST_CONTAINER=true ./gradlew build --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "测试失败" "单元测试执行失败！请检查测试用例。" "false"
    exit 1
fi

# 所有步骤成功完成
./scripts/tool_scripts/show-notification.sh "任务完成" "编译和单元测试已完成！" "true"