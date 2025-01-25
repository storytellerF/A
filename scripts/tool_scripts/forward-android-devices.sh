#!/bin/bash

# 遍历所有已连接的设备并设置端口转发
for device in $(adb devices | grep -w 'device' | cut -f1); do
  echo "Setting up port forwarding for device $device"
  adb -s $device reverse tcp:8888 tcp:8888
done

echo "Port forwarding setup complete!"
