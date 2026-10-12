#!/bin/bash
set -euo pipefail

repository_root=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$repository_root/build"
test_directory=$(mktemp -d "$repository_root/build/linux-desktop-runner-test.XXXXXX")
cleanup() {
    case "$test_directory" in
        "$repository_root"/build/linux-desktop-runner-test.*) rm -rf "$test_directory" ;;
        *) echo 'Unexpected test directory.' >&2; return 1 ;;
    esac
}
trap cleanup EXIT
mkdir -p "$test_directory/scripts/test_scripts" "$test_directory/bin"
cp "$repository_root/scripts/test_scripts/run-linux-desktop-e2e.sh" "$test_directory/scripts/test_scripts/"

cat > "$test_directory/bin/desktop-process" <<'MOCK'
#!/bin/bash
printf '%s\n' "$$" >> "$DESKTOP_PID_LOG"
if [[ ${0##*/} == Xvfb ]]; then printf '99\n' >&3; fi
exec sleep 60
MOCK
for command in Xvfb openbox appium; do
    cp "$test_directory/bin/desktop-process" "$test_directory/bin/$command"
    chmod +x "$test_directory/bin/$command"
done
cat > "$test_directory/bin/xprop" <<'MOCK'
#!/bin/bash
printf '_NET_SUPPORTING_WM_CHECK(WINDOW): window id # 0x1\n'
MOCK
for command in xdpyinfo curl; do
    printf '#!/bin/bash\nexit 0\n' > "$test_directory/bin/$command"
done
chmod +x "$test_directory/bin/xprop" "$test_directory/bin/xdpyinfo" "$test_directory/bin/curl"
cat > "$test_directory/gradlew" <<'MOCK'
#!/bin/bash
printf 'call\n' >> "$DESKTOP_GRADLE_CALLS"
printf '%s\n' "$@" > "$DESKTOP_GRADLE_LOG"
exit "${DESKTOP_GRADLE_EXIT:-0}"
MOCK
chmod +x "$test_directory/gradlew"

runCase() {
    local expected_exit=$1
    shift
    : > "$test_directory/calls"
    : > "$test_directory/pids"
    local actual_exit=0
    (
        cd "$test_directory"
        PATH="$test_directory/bin:$PATH" \
        DESKTOP_PID_LOG="$test_directory/pids" \
        DESKTOP_GRADLE_CALLS="$test_directory/calls" \
        DESKTOP_GRADLE_LOG="$test_directory/arguments" \
        DESKTOP_GRADLE_EXIT="$expected_exit" \
            bash scripts/test_scripts/run-linux-desktop-e2e.sh --desktop-session "$@"
    ) || actual_exit=$?
    [[ $actual_exit == "$expected_exit" ]] || { echo 'Gradle exit status was lost.' >&2; exit 1; }
    [[ $(wc -l < "$test_directory/calls") == 1 ]] || { echo 'Expected one Gradle invocation.' >&2; exit 1; }
    [[ $(wc -l < "$test_directory/pids") == 3 ]] || { echo 'Expected three owned desktop processes.' >&2; exit 1; }
    while read -r pid; do
        if kill -0 "$pid" 2>/dev/null; then
            echo "Owned desktop process was not cleaned up: $pid" >&2
            kill "$pid" 2>/dev/null || true
            exit 1
        fi
    done < "$test_directory/pids"
}

runCase 0 --tests 'com.example.FirstTest.method with spaces' --tests=com.example.SecondTest \
    --no-debug-jvm --test-dry-run --rerun --max-workers=4 \
    -Pserver.flavor=sample -Pserver.buildType=test
expected_arguments=$(printf '%s\n' \
    ':app:desktopAppium:appiumTest' \
    '--tests' 'com.example.FirstTest.method with spaces' '--tests=com.example.SecondTest' \
    '--no-debug-jvm' '--test-dry-run' '--rerun' \
    ':panel:desktopAppium:appiumTest' \
    '--tests' 'com.example.FirstTest.method with spaces' '--tests=com.example.SecondTest' \
    '--no-debug-jvm' '--test-dry-run' '--rerun' \
    '--continue' '--console=plain' '--max-workers=4' \
    '-Pserver.flavor=sample' '-Pserver.buildType=test')
[[ $(cat "$test_directory/arguments") == "$expected_arguments" ]] || {
    echo 'Task options must apply to both suites; global options must appear once.' >&2
    exit 1
}
runCase 7 -Pserver.flavor=sample -Pserver.buildType=test
: > "$test_directory/calls"
: > "$test_directory/pids"
if (
    cd "$test_directory"
    PATH="$test_directory/bin:$PATH" \
    DESKTOP_PID_LOG="$test_directory/pids" \
    DESKTOP_GRADLE_CALLS="$test_directory/calls" \
    DESKTOP_GRADLE_LOG="$test_directory/arguments" \
        bash scripts/test_scripts/run-linux-desktop-e2e.sh --desktop-session --tests
) >/dev/null 2>&1; then
    echo 'Missing test filter should fail before starting desktop processes.' >&2
    exit 1
fi
[[ ! -s "$test_directory/calls" && ! -s "$test_directory/pids" ]] || {
    echo 'Invalid arguments must not launch Gradle or desktop processes.' >&2
    exit 1
}
