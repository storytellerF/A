@echo off
setlocal

:: 检查是否提供了端口参数
if "%1"=="" (
    echo Please provide a port number.
    exit /b 1
)

set PORT=%1

:: 获取所有已连接的设备并设置端口转发
for /f "tokens=1" %%d in ('adb devices ^| findstr /R /C:"device"') do (
    echo Setting up port forwarding for device %%d on port %PORT%
    adb -s %%d reverse tcp:%PORT% tcp:%PORT%
)

echo Port forwarding setup complete on port %PORT%!
