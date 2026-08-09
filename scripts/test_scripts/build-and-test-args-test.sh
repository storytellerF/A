#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
test_directory=$(mktemp -d)
trap 'rm -rf "$test_directory"' EXIT

mkdir -p "$test_directory/scripts/test_scripts" "$test_directory/scripts/tool_scripts"
cp "$repository_root/scripts/test_scripts/build-and-test.sh" "$test_directory/scripts/test_scripts/build-and-test.sh"

printf '%s\n' \
  '#!/bin/sh' \
  'exit 0' \
  > "$test_directory/scripts/tool_scripts/exec-until-success.sh"
chmod +x "$test_directory/scripts/tool_scripts/exec-until-success.sh"

printf '%s\n' \
  '#!/bin/sh' \
  ': > "$BUILD_AND_TEST_LOG"' \
  'printf "COUNT=%s\n" "$#" >> "$BUILD_AND_TEST_LOG"' \
  'for argument in "$@"; do' \
  '  printf "%s\n" "$argument" >> "$BUILD_AND_TEST_LOG"' \
  'done' \
  'if [ -n "${BUILD_AND_TEST_MARKER:-}" ]; then' \
  '  printf "%s\n" "$*" >> "$BUILD_AND_TEST_MARKER"' \
  'fi' \
  'if [ -n "${BUILD_AND_TEST_DELAY_MARKER:-}" ] && printf "%s\n" "$*" | grep -q appiumTest && mkdir "$BUILD_AND_TEST_DELAY_MARKER" 2>/dev/null; then' \
  '  sleep "${BUILD_AND_TEST_DELAY_SECONDS:-3}"' \
  'fi' \
  > "$test_directory/gradlew"
chmod +x "$test_directory/gradlew"

argument_log="$test_directory/gradle-arguments.log"
: > "$test_directory/com.example.ExpandedTest"
(
  cd "$test_directory"
  BUILD_AND_TEST_LOG="$argument_log" \
    ./scripts/test_scripts/build-and-test.sh \
      --unit \
      --tests \
      'com.example.FirstTest' \
      'com.example.SecondTest.method with spaces' \
      --tests \
      'com.example.ThirdTest' \
      'com.example.*Test' \
      --plain
)

expected_arguments=$(printf '%s\n' \
  'COUNT=3' \
  'check' \
  '-PbuildAndTest.testFilters=com.example.FirstTest
com.example.SecondTest.method with spaces
com.example.ThirdTest
com.example.*Test' \
  '--console=plain')
actual_arguments=$(sed -n '1,$p' "$argument_log")

if [ "$actual_arguments" != "$expected_arguments" ]; then
  echo "Unexpected Gradle arguments:"
  printf '%s\n' "$actual_arguments"
  exit 1
fi

if (
  cd "$test_directory"
  ./scripts/test_scripts/build-and-test.sh --unit --tests --plain
) >/dev/null 2>&1; then
  echo "--tests without values should fail"
  exit 1
fi

printf '%s\n' \
  '#!/bin/sh' \
  'if [ "$1" = "devices" ]; then' \
  '  printf "List of devices attached\\nphysical-device\\tdevice\\n"' \
  '  exit 0' \
  'fi' \
  'if [ "$1" = "connect" ]; then' \
  '  echo "VMware connection must not be attempted when a physical device is connected" >&2' \
  '  exit 1' \
  'fi' \
  'if [ "$1" = "-s" ]; then' \
  '  shift 2' \
  'fi' \
  'if [ "$1" = "shell" ]; then' \
  '  shift' \
  'fi' \
  'case "$*" in' \
  '  *"getprop sys.boot_completed"*) printf "1\\n" ;;' \
  '  "mkdir /data/local/tmp/appium-device-test.lock.d") mkdir "$ADB_FAKE_LOCK_DIR" ;;' \
  '  "cat > /data/local/tmp/appium-device-test.lock.d/lock.json")' \
  '    cat > "$ADB_FAKE_LOCK_DIR/lock.json"' \
  '    printf "write\\n" >> "$ADB_LOCK_WRITE_LOG"' \
  '    ;;' \
  '  "cat /data/local/tmp/appium-device-test.lock.d/lock.json") cat "$ADB_FAKE_LOCK_DIR/lock.json" 2>/dev/null || true ;;' \
  '  "rm -rf /data/local/tmp/appium-device-test.lock.d") rm -rf "$ADB_FAKE_LOCK_DIR" ;;' \
  'esac' \
  > "$test_directory/adb"
chmod +x "$test_directory/adb"

lock_write_log="$test_directory/device-lock-writes.log"
: > "$lock_write_log"
(
  cd "$test_directory"
  PATH="$test_directory:$PATH" \
    ADB_FAKE_LOCK_DIR="$test_directory/device-lock" \
    ADB_LOCK_WRITE_LOG="$lock_write_log" \
    APPIUM_DEVICE_LOCK_HEARTBEAT_SECONDS=1 \
    BUILD_AND_TEST_LOG="$argument_log" \
    BUILD_AND_TEST_DELAY_MARKER="$test_directory/e2e-delay" \
    BUILD_AND_TEST_MARKER="$test_directory/e2e-gradle-tasks.log" \
    ./scripts/test_scripts/build-and-test.sh --e2e --plain
)

expected_e2e_arguments=$(printf '%s\n' \
  'COUNT=3' \
  ':app:cliE2e:e2eTest' \
  ':panel:cliE2e:e2eTest' \
  '--console=plain')
actual_e2e_arguments=$(sed -n '1,$p' "$argument_log")

if [ "$actual_e2e_arguments" != "$expected_e2e_arguments" ]; then
  echo "Unexpected CLI E2E Gradle arguments:"
  printf '%s\n' "$actual_e2e_arguments"
  exit 1
fi

if ! grep -q ':app:androidApp:appiumTest' "$test_directory/e2e-gradle-tasks.log"; then
  echo "--e2e did not run Appium tests"
  exit 1
fi

lock_write_count=$(wc -l < "$lock_write_log")
if [ "$lock_write_count" -lt 2 ]; then
  echo "Appium device lock lease was not refreshed"
  exit 1
fi
