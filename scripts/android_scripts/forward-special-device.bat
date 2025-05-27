@echo off
setlocal

:: 检查参数个数
if "%~2"=="" (
    echo 错误：需要提供两个参数：android_id 和端口号。
    echo 用法：%0 <android_id> <port>
    exit /b 1
)

:: 获取输入的 android_id 和 port
set input_android_id=%~1
set input_port=%~2

:: 检查端口号是否是有效的数字
echo %input_port% | findstr /r "^[0-9]*$" >nul
if errorlevel 1 (
    echo 错误：端口号必须是数字。
    exit /b 1
)

:: 检查端口号是否在有效范围内（1到65535）
if %input_port% lss 1 (
    echo 错误：端口号必须在 1 到 65535 之间。
    exit /b 1
)
if %input_port% gtr 65535 (
    echo 错误：端口号必须在 1 到 65535 之间。
    exit /b 1
)

:: 获取所有已连接的设备
for /f "tokens=1" %%d in ('adb devices ^| findstr /R /C:"device"') do (

    :: 获取当前设备的 android_id
    for /f %%i in ('adb -s %%d shell settings get secure android_id') do (
        set device_android_id=%%i
    )

    :: 去除换行符
    set device_android_id=%device_android_id:~0,16%

    :: 检查设备的 android_id 是否与输入的匹配
    if "%device_android_id%"=="%input_android_id%" (
        echo 找到匹配的设备: %%d (android_id: %device_android_id%)

        :: 设置端口转发
        echo 为设备 %%d 设置端口转发: localhost:%input_port% -> localhost:%input_port%
        adb -s %%d reverse tcp:%input_port% tcp:%input_port%

        echo 端口转发已设置成功！
        exit /b 0
    )
)

:: 如果没有找到匹配的设备
echo 未找到匹配的设备，无法设置端口转发。
exit /b 1
