#!/bin/bash
set -euo pipefail

# A disconnected Xwayland desktop can block Skiko swapBuffers on the EDT and
# make AT-SPI unresponsive. Own a display and accessibility bus for this run.
repo_dir=$(cd "$(dirname "$0")/../.." && pwd)
cd "$repo_dir"
if [[ ${1:-} != --desktop-session ]]; then
    [[ $(uname -s) == Linux ]] || { echo 'This runner requires Linux.' >&2; exit 1; }
    exec dbus-run-session -- bash "$0" --desktop-session "$@"
fi
shift

test_args=()
gradle_args=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --tests)
            [[ $# -ge 2 && -n "$2" && "$2" != --* ]] || { echo '--tests requires a pattern.' >&2; exit 1; }
            test_args+=("$1" "$2")
            shift 2
            ;;
        --tests=*)
            [[ -n "${1#*=}" ]] || { echo '--tests requires a pattern.' >&2; exit 1; }
            test_args+=("$1")
            shift
            ;;
        --debug-jvm|--no-debug-jvm|--test-dry-run|--no-test-dry-run|--fail-fast|--no-fail-fast|--rerun)
            test_args+=("$1")
            shift
            ;;
        *)
            gradle_args+=("$1")
            shift
            ;;
    esac
done

for command in Xvfb openbox xdpyinfo xprop appium curl; do
    command -v "$command" >/dev/null || { echo "Missing desktop dependency: $command" >&2; exit 1; }
done
mkdir -p build
run_dir=$(mktemp -d "$repo_dir/build/linux-desktop-e2e.XXXXXX")
echo "Desktop diagnostics: $run_dir"
owned_pids=()
cleanup() {
    local pid
    for pid in "${owned_pids[@]}"; do kill "$pid" 2>/dev/null || true; done
    for pid in "${owned_pids[@]}"; do wait "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# -displayfd allocates an unused display and reports it only when ready.
Xvfb -displayfd 3 -screen 0 1600x1000x24 -nolisten tcp -ac \
    3>"$run_dir/display" >"$run_dir/xvfb.log" 2>&1 &
owned_pids+=("$!")
for ((i=0; i<100; i++)); do
    [[ -s "$run_dir/display" ]] && break
    kill -0 "${owned_pids[0]}" 2>/dev/null || { echo 'Virtual display failed to start.' >&2; exit 1; }
    sleep 0.1
done
[[ -s "$run_dir/display" ]] || { echo 'Virtual display did not become ready.' >&2; exit 1; }
export DISPLAY=":$(cat "$run_dir/display")"
unset XAUTHORITY
xdpyinfo >"$run_dir/display-info.txt"

openbox --sm-disable >"$run_dir/openbox.log" 2>&1 &
owned_pids+=("$!")
for ((i=0; i<100; i++)); do
    xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id' && break
    kill -0 "${owned_pids[1]}" 2>/dev/null || { echo 'Window manager failed to start.' >&2; exit 1; }
    sleep 0.1
done
xprop -root _NET_SUPPORTING_WM_CHECK | grep -q 'window id' || { echo 'Window manager did not become ready.' >&2; exit 1; }

appium_port=${APPIUM_DESKTOP_PORT:-4727}
export APPIUM_DESKTOP_SERVER_URL="http://127.0.0.1:$appium_port"
appium --address 127.0.0.1 --port "$appium_port" >"$run_dir/appium.log" 2>&1 &
owned_pids+=("$!")
for ((i=0; i<100; i++)); do
    kill -0 "${owned_pids[2]}" 2>/dev/null || { echo 'Appium failed to start.' >&2; exit 1; }
    curl -sf "$APPIUM_DESKTOP_SERVER_URL/status" >/dev/null && break
    sleep 0.1
done
curl -sf "$APPIUM_DESKTOP_SERVER_URL/status" >/dev/null || { echo 'Appium did not become ready.' >&2; exit 1; }

# The platform BuildService serializes desktop tests; continue after either suite fails.
./gradlew \
    :app:desktopAppium:appiumTest "${test_args[@]}" \
    :panel:desktopAppium:appiumTest "${test_args[@]}" \
    --continue --console=plain "${gradle_args[@]}"
