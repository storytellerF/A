#!/bin/sh
set -e

# Parsing Arguments
RUN_UNIT=false
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
  RUN_ANDROID=true
  RUN_DESKTOP=true
  RUN_APPIUM=true
fi

shutdownEmu() {
    echo "Shutting down emulator gracefully..."
    adb emu kill || true
    echo "Emulator shut down."
}

setupAvd() {
    echo "Setting up Android Emulator..."
    ./scripts/android_scripts/create-avd.sh device-test "system-images;android-36;google_apis;x86_64" "pixel"
    ./scripts/android_scripts/start-avd.sh device-test
}

cleanup() {
    # Only run cleanup for things we started
    ([ "$RUN_ANDROID" = true ] || [ "$RUN_APPIUM" = true ]) && shutdownEmu
}
trap cleanup EXIT

# Unit Tests
if [ "$RUN_UNIT" = true ]; then
    echo "Running Unit Tests..."
    ./gradlew test $TEST_ARGS --no-daemon $GRADLE_CONSOLE_ARGS
fi

if [ "$RUN_APPIUM" = true ]; then
    echo "Preparing server/worker images for Appium Testcontainers..."
    ./scripts/build_scripts/build-server-worker-images.sh dev prod host
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
