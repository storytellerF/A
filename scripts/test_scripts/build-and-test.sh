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

checkEmulatorReady() {
    if ! command -v adb >/dev/null 2>&1; then
        echo "adb is not available. Start a booted Android emulator before running Android/Appium tests."
        exit 1
    fi

    emulator_serials=$(adb devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1 }')
    if [ -z "$emulator_serials" ]; then
        echo "No running Android emulator found. Start and fully boot an emulator before running Android/Appium tests."
        exit 1
    fi

    for emulator_serial in $emulator_serials; do
        boot_completed=$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
        if [ "$boot_completed" = "1" ]; then
            echo "Android emulator is ready: $emulator_serial"
            return 0
        fi
    done

    echo "Android emulator is running but has not completed boot. Wait until sys.boot_completed=1 before running Android/Appium tests."
    exit 1
}

copyAppiumLogsToBuild() {
  src_dir="dev/appium/build/test/appium-logs"
  dest_dir="build/test/appium-logs"
  if [ -d "$src_dir" ]; then
    mkdir -p "$dest_dir"
    cp -R "$src_dir"/. "$dest_dir"/
    echo "Copied Appium logs to $dest_dir"
  fi
}

# Unit Tests
if [ "$RUN_UNIT" = true ]; then
    echo "Running Unit Tests..."
    ./gradlew test $TEST_ARGS $GRADLE_CONSOLE_ARGS
fi

if [ "$RUN_APPIUM" = true ]; then
    echo "Preparing server/worker images for Appium Testcontainers..."
    ./scripts/build_scripts/build-server-worker-images.sh dev prod host
    echo "Building Release APK..."
    ./gradlew app:android:assembleDebug $GRADLE_CONSOLE_ARGS
    ./gradlew panel:android:assembleDebug $GRADLE_CONSOLE_ARGS
fi

if [ "$RUN_ANDROID" = true ] || [ "$RUN_APPIUM" = true ]; then
    checkEmulatorReady
fi

# Running android Tests
if [ "$RUN_ANDROID" = true ]; then
    echo "Running Android Connected Tests..."
    ./gradlew ${MODULE}:connectedAndroidTest $TEST_ARGS $GRADLE_CONSOLE_ARGS
fi

# Running desktop Tests
if [ "$RUN_DESKTOP" = true ]; then
    echo "Running Desktop Tests..."
    ./gradlew ${MODULE}:desktopTest $TEST_ARGS $GRADLE_CONSOLE_ARGS
fi

if [ "$RUN_APPIUM" = true ]; then
    echo "Running Appium Tests..."
    sudo rm -rf ./dev/appium/build/test/appium/sessions
    ./gradlew :dev:appium:clean -Pappium=true $GRADLE_CONSOLE_ARGS
    appium_exit=0
    ./gradlew :dev:appium:test -Pappium=true $TEST_ARGS $GRADLE_CONSOLE_ARGS || appium_exit=$?
    copyAppiumLogsToBuild
    if [ "$appium_exit" -ne 0 ]; then
      exit "$appium_exit"
    fi
fi
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
