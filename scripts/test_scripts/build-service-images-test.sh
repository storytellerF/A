#!/bin/bash
set -euo pipefail
repository_root=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$repository_root/build"
test_directory=$(mktemp -d "$repository_root/build/service-images-test.XXXXXX")
cleanup() {
    case "$test_directory" in
        "$repository_root"/build/service-images-test.*) rm -rf "$test_directory" ;;
        *) return 1 ;;
    esac
}
trap cleanup EXIT
mkdir -p "$test_directory/scripts/build_scripts" "$test_directory/bin"
cp "$repository_root/scripts/build_scripts/build-service-images.sh" "$test_directory/scripts/build_scripts/"
cat > "$test_directory/scripts/build_scripts/build-cloud.sh" <<'MOCK'
#!/bin/bash
touch "$IMAGE_TEST_RUN/host-build"
MOCK
chmod +x "$test_directory/scripts/build_scripts/build-cloud.sh"
cat > "$test_directory/bin/docker" <<'MOCK'
#!/bin/bash
set -euo pipefail
target=
while [[ $# -gt 0 ]]; do
    if [[ $1 == --target ]]; then target=$2; shift; fi
    shift
done
[[ -n $target ]]
touch "$IMAGE_TEST_RUN/started/$target"
ready=false
for ((i=0; i<100; i++)); do
    if [[ $(find "$IMAGE_TEST_RUN/started" -type f | wc -l) == 4 ]]; then ready=true; break; fi
    sleep 0.05
done
[[ $ready == true ]] || { echo 'All four builds must start before any completes.' >&2; exit 1; }
touch "$IMAGE_TEST_RUN/finished/$target"
if [[ $target == worker ]]; then exit "${IMAGE_TEST_FAILURE:-0}"; fi
MOCK
chmod +x "$test_directory/bin/docker"
for mode in local docker; do
    run_directory="$test_directory/$mode"
    mkdir -p "$run_directory/started" "$run_directory/finished"
    failure=0
    [[ $mode == docker ]] && failure=7
    actual_exit=0
    (
        cd "$test_directory"
        PATH="$test_directory/bin:$PATH" IMAGE_TEST_RUN="$run_directory" IMAGE_TEST_FAILURE="$failure" \
            bash scripts/build_scripts/build-service-images.sh "$mode"
    ) || actual_exit=$?
    [[ $actual_exit == "$failure" ]]
    [[ $(find "$run_directory/finished" -type f | wc -l) == 4 ]]
    if [[ $mode == local ]]; then
        [[ -f "$run_directory/host-build" ]]
    else
        [[ ! -f "$run_directory/host-build" ]]
    fi
done
