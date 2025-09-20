#!/bin/bash

# 定义颜色
RED='\033[0;31m'      # 红色
GREEN='\033[0;32m'    # 绿色
YELLOW='\033[1;33m'   # 黄色
CYAN='\033[0;36m'     # 青色
NC='\033[0m'          # 无色 (reset)

# 检查参数个数
if [ $# -ne 2 ]; then
    echo -e "${RED}❌ 错误：需要提供两个参数：android_id 和端口号。${NC}"
    echo -e "${YELLOW}用法：$0 <android_id> <port>${NC}"
    exit 1
fi

# 获取传入的 android_id 和 port
input_android_id=$1
input_port=$2

# 检查端口号是否是有效的数字
if ! [[ "$input_port" =~ ^[0-9]+$ ]]; then
    echo -e "${RED}❌ 错误：端口号必须是数字。${NC}"
    exit 1
fi

# 检查端口号范围（有效端口号范围：1-65535）
if [ "$input_port" -lt 1 ] || [ "$input_port" -gt 65535 ]; then
    echo -e "${RED}❌ 错误：端口号必须在 1 到 65535 之间。${NC}"
    exit 1
fi

# 遍历所有已连接的设备
found=false
for device in $(adb devices | grep -w 'device' | cut -f1); do
    # 获取当前设备的 android_id
    device_android_id=$(adb -s "$device" shell settings get secure android_id | tr -d '\r\n')

    # 检查设备的 android_id 是否与输入的匹配
    if [ "$device_android_id" == "$input_android_id" ]; then
        echo -e "${GREEN}✅ 找到匹配的设备:${NC} ${CYAN}$device${NC} ${YELLOW}(android_id: $device_android_id)${NC}"

        # 设置端口转发
        echo -e "${CYAN}🔗 为设备 $device 设置端口转发:${NC} localhost:${GREEN}$input_port${NC} -> localhost:${GREEN}$input_port${NC}"
        adb -s "$device" reverse tcp:"$input_port" tcp:"$input_port"

        echo -e "${GREEN}🎉 端口转发已设置成功！${NC}"
        found=true
        break
    fi
done

# 如果没有找到匹配的设备
if [ "$found" = false ]; then
    echo -e "${RED}⚠️ 未找到匹配的设备，无法设置端口转发。${NC}"
    exit 1
fi
