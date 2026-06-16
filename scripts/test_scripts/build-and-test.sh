#!/bin/sh
set -e

# Parsing Arguments
RUN_ANDROID=false
RUN_DESKTOP=false
RUN_APPIUM=false
RUN_COMPILE_UNIT=false
RUN_COMPOSE=false
MODULE="app:composeApp"
TEST_ARGS=""
GRADLE_CONSOLE_ARGS=""
RUN_ALL=false
EXEC_MODE="both"  # "prepare", "run", or "both"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --all) RUN_ALL=true; shift ;;
    --android) RUN_ANDROID=true; shift ;;
    --desktop) RUN_DESKTOP=true; shift ;;
    --appium) RUN_APPIUM=true; shift ;;
    --compose) RUN_COMPOSE=true; shift ;;
    --unit) RUN_COMPILE_UNIT=true; shift ;;
    --plain) GRADLE_CONSOLE_ARGS="--console=plain"; shift ;;
    --prepare) EXEC_MODE="prepare"; shift ;;
    --run) EXEC_MODE="run"; shift ;;
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

if [ "$RUN_ALL" = true ]; then
  RUN_ANDROID=true
  RUN_DESKTOP=true
  RUN_APPIUM=true
  RUN_COMPILE_UNIT=true
  RUN_COMPOSE=true
fi

showNotification() {
    title="$1"
    message="$2"
    success="$3"
    if [ -x ./scripts/tool_scripts/show-notification.sh ]; then
        ./scripts/tool_scripts/show-notification.sh "$title" "$message" "$success"
    fi
}

isVmwareEnvironment() {
    if command -v systemd-detect-virt >/dev/null 2>&1 && systemd-detect-virt --quiet --vm && [ "$(systemd-detect-virt 2>/dev/null)" = "vmware" ]; then
        return 0
    fi

    if [ -r /sys/class/dmi/id/product_name ] && grep -qi 'vmware' /sys/class/dmi/id/product_name; then
        return 0
    fi

    return 1
}

vmwareHostIp() {
    if ! command -v ip >/dev/null 2>&1; then
        return 1
    fi

    local_ip=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }')
    [ -z "$local_ip" ] && local_ip=$(ip -4 addr show scope global 2>/dev/null | awk '/inet / { sub(/\/.*/, "", $2); print $2; exit }')
    [ -z "$local_ip" ] && return 1

    echo "$local_ip" | awk -F. 'NF == 4 { print $1 "." $2 "." $3 ".1" }'
}

findEmulatorSerials() {
    emulator_serials=""
    for device_serial in $(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
        is_emulator=$(adb -s "$device_serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')
        if [ "$is_emulator" = "1" ]; then
            emulator_serials="${emulator_serials}${emulator_serials:+ }$device_serial"
        fi
    done
    echo "$emulator_serials"
}

tryConnectHostEmulator() {
    host=$(vmwareHostIp)
    if [ -z "$host" ]; then
        echo "VMware environment detected, but failed to derive host IP from local IPv4 address."
        return 1
    fi

    echo "No local Android emulator found. VMware environment detected; trying host emulator via adb connect $host..."

    for port in 5555 5557 5559 5561 5563 5565; do
        target="$host:$port"
        connect_output=$(adb connect "$target" 2>&1 || true)
        echo "adb connect $target: $connect_output"

        emulator_serials=$(findEmulatorSerials)
        if [ -n "$emulator_serials" ]; then
            return 0
        fi
    done

    return 1
}

checkEmulatorReady() {
    if ! command -v adb >/dev/null 2>&1; then
        echo "adb is not available. Start a booted Android emulator before running Android/Appium tests."
        exit 1
    fi

    emulator_serials=$(findEmulatorSerials)

    if [ -z "$emulator_serials" ]; then
        if isVmwareEnvironment && tryConnectHostEmulator; then
            emulator_serials=$(findEmulatorSerials)
        fi

        if [ -z "$emulator_serials" ]; then
            echo "No running Android emulator found. Start and fully boot an emulator before running Android/Appium tests."
            if isVmwareEnvironment; then
                echo "VMware environment detected, but no host emulator accepted adb connections."
            fi
            exit 1
        fi
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

# Check emulator readiness (for Android and Appium tests)
if [ "$RUN_ANDROID" = true ] || [ "$RUN_APPIUM" = true ]; then
    checkEmulatorReady
fi

if [ "$RUN_COMPILE_UNIT" = true ]; then
    echo "Running compile checks..."
    if ! ./gradlew assemble $GRADLE_CONSOLE_ARGS; then
        showNotification "编译失败" "请检查编译错误" "false"
        exit 1
    fi

    echo "Running detekt..."
    if ! ./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt $GRADLE_CONSOLE_ARGS; then
        showNotification "Detekt 失败" "代码静态分析失败！请检查代码规范问题。" "false"
        exit 1
    fi

    echo "Cleaning test cache..."
    if ! ./gradlew clean -Pappium=false $GRADLE_CONSOLE_ARGS; then
        showNotification "清理失败" "clean 执行失败！" "false"
        exit 1
    fi

    echo "Running unit tests without Testcontainers..."
    if ! ENABLE_TEST_CONTAINER=false ./gradlew test -Pappium=false $TEST_ARGS $GRADLE_CONSOLE_ARGS; then
        showNotification "测试失败" "单元测试执行失败！请检查测试用例。" "false"
        exit 1
    fi

    echo "Running unit tests with Testcontainers..."
    if ! ENABLE_TEST_CONTAINER=true ./gradlew test -Pappium=false $TEST_ARGS $GRADLE_CONSOLE_ARGS; then
        showNotification "测试失败" "单元测试执行失败！请检查测试用例。" "false"
        exit 1
    fi

    showNotification "任务完成" "编译和单元测试已完成！" "true"
fi

if [ "$RUN_COMPOSE" = true ]; then
    echo "Running compose service script tests..."
    ./scripts/test_scripts/compose-service-test.sh
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

# Running Appium Tests
if [ "$RUN_APPIUM" = true ]; then
    echo "Running Appium Tests..."
    rm -rf ./dev/appium/build/test/appium/sessions
    ./gradlew :dev:appium:clean -Pappium=true $GRADLE_CONSOLE_ARGS
    appium_exit=0
    ./gradlew :dev:appium:test -Pappium=true $TEST_ARGS $GRADLE_CONSOLE_ARGS || appium_exit=$?
    if [ "$appium_exit" -ne 0 ]; then
        exit "$appium_exit"
    fi
fi
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
