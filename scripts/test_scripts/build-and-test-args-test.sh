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
  'COUNT=4' \
  'check' \
  '-Pappium=false' \
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
