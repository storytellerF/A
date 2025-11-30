@echo off
if "%~1"=="" (
    echo 用法: %~n0 port
    echo 示例: %~n0 8080
    exit /b
)

set "port=%~1"
set "pid="

for /f "tokens=5" %%i in ('netstat -ano ^| findstr :%port%') do (
    set "pid=%%i"
)

if not defined pid (
    echo 端口 %port% 未被占用，跳过。
    exit /b
)

echo 检测到 PID %pid% 占用端口 %port%，正在结束...
taskkill /F /PID %pid%
