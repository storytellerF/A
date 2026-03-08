#!/bin/sh
set -e

# OS Detection
if [ "$(uname)" = "Darwin" ] || [ "$(expr substr $(uname -s) 1 5)" = "Linux" ]; then
    KILL_PORT_SCRIPT="./scripts/tool_scripts/kill-port.sh"
else
    KILL_PORT_SCRIPT="./scripts/tool_scripts/kill-port.bat"
fi

# Parsing Arguments
RUN_UNIT=false
RUN_UI=false
RUN_ANDROID=false
RUN_DESKTOP=false
RUN_APPIUM=false
MODULE="app:composeApp"
TEST_ARGS=""
GRADLE_CONSOLE_ARGS=""
RUN_ALL=false

while [ "$#" -gt 0 ]; do
  case "$1" in
    --all) RUN_ALL=true; shift ;;
    --unit) RUN_UNIT=true; shift ;;
    --ui) RUN_UI=true; shift ;;
    --android) RUN_ANDROID=true; shift ;;
    --desktop) RUN_DESKTOP=true; shift ;;
    --appium) RUN_APPIUM=true; shift ;;
    --plain) GRADLE_CONSOLE_ARGS="--console=plain"; shift ;;
    --module)
      [ -z "$2" ] && { echo "--module requires a value"; exit 1; }
      MODULE="$2"
      shift 2
      ;;
    --tests)
      [ -z "$2" ] && { echo "--tests requires a value"; exit 1; }
      TEST_ARGS="$TEST_ARGS --tests \"$2\""
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      exit 1
      ;;
  esac
done

# Default behavior: Run all if no specific filters provided
if [ "$RUN_ALL" = true ]; then
  RUN_UNIT=true
  RUN_UI=true
  RUN_ANDROID=true
  RUN_DESKTOP=true
  RUN_APPIUM=true
fi

# If --ui specified but no platform, default to both
if [ "$RUN_UI" = true ] && [ "$RUN_ANDROID" = false ] && [ "$RUN_DESKTOP" = false ]; then
  RUN_ANDROID=true
  RUN_DESKTOP=true
fi

# If platform specified, imply UI tests for that platform
if [ "$RUN_ANDROID" = true ] || [ "$RUN_DESKTOP" = true ]; then
  RUN_UI=true
fi

shutdownEmu() {
    echo "Shutting down emulator gracefully..."
    adb emu kill || true
    echo "Emulator shut down."
}

shutdownServer() {
    echo "Shutting down server gracefully..."
    pid=$(lsof -t -i :8888) && kill -9 "$pid"
    echo "Server shut down."
}

setupAvd() {
    echo "Setting up Android Emulator..."
    ./scripts/android_scripts/create-avd.sh device-test "system-images;android-36;google_apis;x86_64" "pixel"
    ./scripts/android_scripts/start-avd.sh device-test
    ./scripts/android_scripts/forward-android-devices.sh 8811
}

cleanup() {
    # Only run cleanup for things we started
    ([ "$RUN_ANDROID" = true ] || [ "$RUN_APPIUM" = true ]) && shutdownEmu
    [ "$RUN_UI" = true ] && shutdownServer
    if [ "$RUN_APPIUM" = true ]; then
        $KILL_PORT_SCRIPT 8811
    fi
}
trap cleanup EXIT

# Unit Tests
if [ "$RUN_UNIT" = true ]; then
    echo "Running Unit Tests..."
    ./gradlew test $TEST_ARGS --no-daemon $GRADLE_CONSOLE_ARGS
fi

# UI/Integration Tests Environment Setup
if [ "$RUN_UI" = true ]; then
    # Android 插桩测试在手机上执行，需要通过特殊服务器创建隔离的测试服务器，暂时用不到
    echo "Setting up Test Server..."
    ./gradlew cloud:server:installDist --no-daemon $GRADLE_CONSOLE_ARGS
    ./scripts/test_scripts/start-test-server.sh
fi

if [ "$RUN_APPIUM" = true ]; then
    echo "Preparing Server for Appium..."
    ./gradlew cloud:server:installDist cloud:worker:installDist cloud:cli:installDist --no-daemon $GRADLE_CONSOLE_ARGS
    echo "Building Release APK..."
    ./gradlew app:android:assembleDebug --no-daemon $GRADLE_CONSOLE_ARGS
fi

if [ "$RUN_ANDROID" = true ] || [ "$RUN_APPIUM" = true ]; then
    setupAvd
fi

# Running android Tests
if [ "$RUN_ANDROID" = true ]; then
    echo "Running Android Connected Tests..."
    ./gradlew ${MODULE}:connectedAndroidTest --no-daemon $TEST_ARGS $GRADLE_CONSOLE_ARGS
fi

# Running desktop Tests
if [ "$RUN_DESKTOP" = true ]; then
    echo "Running Desktop Tests..."
    ./gradlew ${MODULE}:desktopTest --no-daemon $TEST_ARGS $GRADLE_CONSOLE_ARGS
fi

if [ "$RUN_APPIUM" = true ]; then
    echo "Running Appium Tests..."
    ./gradlew :dev:appium:clean --no-daemon $GRADLE_CONSOLE_ARGS
    ./gradlew :dev:appium:test --no-daemon -Pappium=true $TEST_ARGS $GRADLE_CONSOLE_ARGS
fi
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
