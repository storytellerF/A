#!/bin/sh

# 检测操作系统类型
OS_TYPE=$(uname -s)

# 定义通知函数
notify_success() {
    case "$OS_TYPE" in
        MINGW*|MSYS*|CYGWIN*)
            # Windows系统 - 直接使用WScript.Shell的Popup方法并设置置顶标志
            powershell.exe -Command "
                [System.Media.SystemSounds]::Beep.Play()
                \$shell = New-Object -ComObject WScript.Shell
                # 第二个参数0表示无超时，第四个参数4096表示vbSystemModal(置顶) + 64表示vbInformation(信息图标)
                \$shell.Popup('编译和单元测试已完成！', 0, '任务完成', 4160)
            "
            ;;
        Darwin)
            # macOS系统
            osascript -e "display notification \"编译和单元测试已完成！\" with title \"任务完成\" sound name \"Submarine\""
            ;;
        Linux)
            # Linux系统
            if command -v notify-send >/dev/null 2>&1; then
                notify-send -i dialog-information -t 5000 "任务完成" "编译和单元测试已完成！"
                if command -v paplay >/dev/null 2>&1; then
                    # 尝试播放系统提示音
                    paplay /usr/share/sounds/freedesktop/stereo/complete.oga 2>/dev/null || true
                fi
            fi
            ;;
        *)
            # 其他系统，尝试使用基本的通知
            echo "任务完成: 编译和单元测试已完成！"
            ;;
    esac
}

notify_failure() {
    case "$OS_TYPE" in
        MINGW*|MSYS*|CYGWIN*)
            # Windows系统 - 直接使用WScript.Shell的Popup方法并设置置顶标志
            powershell.exe -Command "
                [System.Media.SystemSounds]::Hand.Play()
                \$shell = New-Object -ComObject WScript.Shell
                # 第二个参数0表示无超时，第四个参数4096表示vbSystemModal(置顶) + 16表示vbCritical(错误图标)
                \$shell.Popup('编译或单元测试失败！请检查控制台输出。', 0, '任务失败', 4112)
            "
            ;;
        Darwin)
            # macOS系统
            osascript -e "display notification \"编译或单元测试失败！请检查控制台输出。\" with title \"任务失败\" sound name \"Sosumi\""
            ;;
        Linux)
            # Linux系统
            if command -v notify-send >/dev/null 2>&1; then
                notify-send -i dialog-error -t 5000 "任务失败" "编译或单元测试失败！请检查控制台输出。"
                if command -v paplay >/dev/null 2>&1; then
                    # 尝试播放错误提示音
                    paplay /usr/share/sounds/freedesktop/stereo/dialog-error.oga 2>/dev/null || true
                fi
            fi
            ;;
        *)
            # 其他系统，尝试使用基本的通知
            echo "任务失败: 编译或单元测试失败！请检查控制台输出。"
            ;;
    esac
}

# 执行命令并捕获结果
if ./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt --no-daemon && \
   ./gradlew cloud:server:installDist --no-daemon && \
   ENABLE_TEST_CONTAINER=true ./gradlew build --no-daemon; then
    notify_success
else
    notify_failure
    exit 1
fi