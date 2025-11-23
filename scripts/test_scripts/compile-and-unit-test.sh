#!/bin/sh

# 执行命令并捕获结果
# 步骤1: 执行 detekt 静态分析
if ! ./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "Detekt 失败" "代码静态分析失败！请检查代码规范问题。" "false"
    exit 1
fi

# 步骤2: 构建 cloud:server
if ! ./gradlew cloud:server:installDist --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "构建失败" "cloud:server:installDist 构建失败！请检查编译错误。" "false"
    exit 1
fi

# 步骤3: 执行完整构建和测试
if ! ENABLE_TEST_CONTAINER=true ./gradlew build --no-daemon; then
    ./scripts/tool_scripts/show-notification.sh "测试失败" "单元测试执行失败！请检查测试用例。" "false"
    exit 1
fi

# 所有步骤成功完成
./scripts/tool_scripts/show-notification.sh "任务完成" "编译和单元测试已完成！" "true"