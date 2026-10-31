#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
test_directory=$(mktemp -d)
trap 'rm -rf "$test_directory"' EXIT

mkdir -p "$test_directory/scripts/test_scripts"
cp "$repository_root/scripts/test_scripts/build-and-test.sh" "$test_directory/scripts/test_scripts/build-and-test.sh"

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
  'case "$*" in' \
  '  *:app:androidAppium:appiumTest*) exit "${BUILD_AND_TEST_APPIUM_EXIT:-0}" ;;' \
  'esac' \
  > "$test_directory/gradlew"
chmod +x "$test_directory/gradlew"

argument_log="$test_directory/gradle-arguments.log"
: > "$test_directory/com.example.ExpandedTest"
(
  cd "$test_directory"
  BUILD_AND_TEST_LOG="$argument_log" \
    ./scripts/test_scripts/build-and-test.sh \
      --desktop \
      --tests \
      'com.example.FirstTest' \
      'com.example.SecondTest.method with spaces' \
      --tests \
      'com.example.ThirdTest' \
      'com.example.*Test' \
      --plain
)

expected_arguments=$(printf '%s\n' \
  'COUNT=12' \
  'app:composeApp:desktopTest' \
  '--tests' \
  'com.example.FirstTest' \
  '--tests' \
  'com.example.SecondTest.method with spaces' \
  '--tests' \
  'com.example.ThirdTest' \
  '--tests' \
  'com.example.*Test' \
  '--console=plain' \
  '-Pserver.flavor=dev' \
  '-Pserver.buildType=debug')
actual_arguments=$(sed -n '1,$p' "$argument_log")

if [ "$actual_arguments" != "$expected_arguments" ]; then
  echo "Unexpected Gradle arguments:"
  printf '%s\n' "$actual_arguments"
  exit 1
fi

if (
  cd "$test_directory"
  ./scripts/test_scripts/build-and-test.sh --desktop --tests --plain
) >/dev/null 2>&1; then
  echo "--tests without values should fail"
  exit 1
fi

for removed_argument in --unit --prepare --run; do
  if (
    cd "$test_directory"
    ./scripts/test_scripts/build-and-test.sh "$removed_argument"
  ) >/dev/null 2>&1; then
    echo "$removed_argument should be rejected"
    exit 1
  fi
done

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
  '  *"appium-device-test.lock.d"*)' \
  '    echo "build-and-test.sh must not manage the Appium device lock" >&2' \
  '    exit 1' \
  '    ;;' \
  'esac' \
  > "$test_directory/adb"
chmod +x "$test_directory/adb"

(
  cd "$test_directory"
  PATH="$test_directory:$PATH" \
    BUILD_AND_TEST_LOG="$argument_log" \
    BUILD_AND_TEST_MARKER="$test_directory/e2e-gradle-tasks.log" \
    ./scripts/test_scripts/build-and-test.sh --e2e --plain
)


for appium_task in \
  :app:androidAppium:appiumTest \
  :app:desktopAppium:appiumTest \
  :app:wasmAppium:appiumTest \
  :panel:androidAppium:appiumTest \
  :panel:desktopAppium:appiumTest \
  :panel:wasmAppium:appiumTest \
  :app:cliE2e:e2eTest \
  :panel:cliE2e:e2eTest
do
  if ! grep -q "$appium_task" "$test_directory/e2e-gradle-tasks.log"; then
    echo "--e2e did not run $appium_task"
    exit 1
  fi
done

expected_appium_call=":app:androidAppium:appiumTest :app:desktopAppium:appiumTest :app:wasmAppium:appiumTest :panel:androidAppium:appiumTest :panel:desktopAppium:appiumTest :panel:wasmAppium:appiumTest :app:cliE2e:e2eTest :panel:cliE2e:e2eTest --continue --console=plain -Pserver.flavor=dev -Pserver.buildType=debug"
expected_detekt_call="detekt --console=plain -Pserver.flavor=dev -Pserver.buildType=debug"
actual_detekt_call=$(sed -n '1p' "$test_directory/e2e-gradle-tasks.log")
[ "$actual_detekt_call" = "$expected_detekt_call" ] || {
  echo "Detekt must check without auto-correct or a worker limit"
  exit 1
}
actual_appium_call=$(sed -n '2p' "$test_directory/e2e-gradle-tasks.log")
[ "$actual_appium_call" = "$expected_appium_call" ] || {
  echo "All E2E targets must share one Gradle invocation with --continue"
  exit 1
}
[ "$(wc -l < "$test_directory/e2e-gradle-tasks.log" | tr -d ' ')" = 2 ]

filtered_marker="$test_directory/filtered-e2e.log"
(
  cd "$test_directory"
  PATH="$test_directory:$PATH" \
    BUILD_AND_TEST_LOG="$argument_log" \
    BUILD_AND_TEST_MARKER="$filtered_marker" \
    ./scripts/test_scripts/build-and-test.sh --e2e --tests 'com.example.*Test' --plain
)
filtered_appium_call=$(sed -n '2p' "$filtered_marker")
expected_filtered_call=""
for appium_task in \
  :app:androidAppium:appiumTest \
  :app:desktopAppium:appiumTest \
  :app:wasmAppium:appiumTest \
  :panel:androidAppium:appiumTest \
  :panel:desktopAppium:appiumTest \
  :panel:wasmAppium:appiumTest \
  :app:cliE2e:e2eTest \
  :panel:cliE2e:e2eTest
do
  expected_filtered_call="${expected_filtered_call}${appium_task} --tests com.example.*Test "
done
expected_filtered_call="${expected_filtered_call}--continue --console=plain -Pserver.flavor=dev -Pserver.buildType=debug"
[ "$filtered_appium_call" = "$expected_filtered_call" ] || {
  echo "Test filters must follow every E2E target"
  exit 1
}

failure_marker="$test_directory/failed-e2e.log"
failure_exit=0
(
  cd "$test_directory"
  PATH="$test_directory:$PATH" \
    BUILD_AND_TEST_LOG="$argument_log" \
    BUILD_AND_TEST_MARKER="$failure_marker" \
    BUILD_AND_TEST_APPIUM_EXIT=7 \
    ./scripts/test_scripts/build-and-test.sh --e2e --plain
) || failure_exit=$?
[ "$failure_exit" = 7 ] || {
  echo "Appium failure status must be preserved"
  exit 1
}
[ "$(wc -l < "$failure_marker" | tr -d ' ')" = 2 ] || {
  echo "All eight E2E targets must share the same invocation, including on failure"
  exit 1
}
