#!/bin/bash

# 检查是否提供了端口参数
if [ -z "$1" ]; then
  echo "Please provide a port number."
  exit 1
fi

PORT=$1

# 遍历所有已连接的设备并设置端口转发
for device in $(adb devices | grep -w 'device' | cut -f1); do
  echo "Setting up port forwarding for device $device on port $PORT"
  adb -s "$device" reverse tcp:"$PORT" tcp:"$PORT"
done

echo "Port forwarding setup complete on port $PORT!"
