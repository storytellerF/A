#!/bin/bash

# 检查参数个数
if [ $# -ne 2 ]; then
    echo "错误：需要提供两个参数：android_id 和端口号。"
    echo "用法：$0 <android_id> <port>"
    exit 1
fi

# 获取传入的 android_id 和 port
input_android_id=$1
input_port=$2

# 检查端口号是否是有效的数字
if ! [[ "$input_port" =~ ^[0-9]+$ ]]; then
    echo "错误：端口号必须是数字。"
    exit 1
fi

# 检查端口号范围（有效端口号范围：1-65535）
if [ "$input_port" -lt 1 ] || [ "$input_port" -gt 65535 ]; then
    echo "错误：端口号必须在 1 到 65535 之间。"
    exit 1
fi

# 遍历所有已连接的设备
for device in $(adb devices | grep -w 'device' | cut -f1); do
    # 获取当前设备的 android_id
    device_android_id=$(adb -s $device shell settings get secure android_id)

    # 去除空格和换行符
    device_android_id=$(echo $device_android_id | tr -d '\r\n')

    # 检查设备的 android_id 是否与输入的匹配
    if [ "$device_android_id" == "$input_android_id" ]; then
        echo "找到匹配的设备: $device (android_id: $device_android_id)"

        # 设置端口转发
        echo "为设备 $device 设置端口转发: localhost:$input_port -> localhost:$input_port"
        adb -s $device reverse tcp:$input_port tcp:$input_port

        echo "端口转发已设置成功！"
        exit 0  # 找到匹配设备并设置转发后退出
    fi
done

# 如果没有找到匹配的设备
echo "未找到匹配的设备，无法设置端口转发。"
exit 1
