@echo off
setlocal enabledelayedexpansion

REM Equivalent to 'set -e' in bash to stop execution on error
set ERRLVL=0
if "%ERRLVL%" neq "0" exit /b %ERRLVL%

REM Define the path to cli.bat
set cli_path=cli\build\install\cli\bin\cli.bat

REM Execute the Gradle task to install the CLI
call gradlew.bat cli:installDist

REM Get the first argument as 'base'
set base=%1

REM Run the 'cli clean' command
call %cli_path% clean

REM Check if the pre_set_user.json file exists and add it
if exist "%base%\data\pre_set_user.json" (
    call %cli_path% add "%base%\data\pre_set_user.json"
)

REM Check if the pre_set_community.json file exists and add it
if exist "%base%\data\pre_set_community.json" (
    call %cli_path% add "%base%\data\pre_set_community.json"
)

REM Check if the pre_set_room.json file exists and add it
if exist "%base%\data\pre_set_room.json" (
    call %cli_path% add "%base%\data\pre_set_room.json"
)

REM Check if the pre_set_topic.json file exists and add it
if exist "%base%\data\pre_set_topic.json" (
    call %cli_path% add "%base%\data\pre_set_topic.json"
)

endlocal
