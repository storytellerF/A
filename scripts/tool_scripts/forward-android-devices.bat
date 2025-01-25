@echo off
setlocal

:: 获取所有已连接的设备并设置端口转发
for /f "tokens=1" %%d in ('adb devices ^| findstr /R /C:"device"') do (
    echo Setting up port forwarding for device %%d
    adb -s %%d reverse tcp:8888 tcp:8888
)

echo Port forwarding setup complete!
