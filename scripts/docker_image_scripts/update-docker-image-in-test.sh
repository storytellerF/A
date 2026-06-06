#!/bin/sh
set -e

cd "$(dirname "$0")/../.."

OUTPUT_FILE="docker-image-in-test"
TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

find_testcontainer_images() {
  if command -v rg >/dev/null 2>&1; then
    rg --no-filename --only-matching \
      '[[:alnum:]_.-]+(/[[:alnum:]_.-]+)+:[[:alnum:]_.-]+' \
      backend cloud dev app client \
      -g '!**/build/**' \
      -g '*.kt' \
      -g '*.kts' \
      2>/dev/null
  else
    find backend cloud dev app client \
      -path '*/build/*' -prune -o \
      \( -name '*.kt' -o -name '*.kts' \) -type f -print \
      | xargs grep -Eoh '[[:alnum:]_.-]+(/[[:alnum:]_.-]+)+:[[:alnum:]_.-]+' 2>/dev/null
  fi \
    | grep -v '^a-\(server\|worker\):' \
    || true
}

find_ryuk_image() {
  testcontainers_version="$(
    sed -n 's/^testContainer[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' gradle/libs.versions.toml | head -n 1
  )"

  if [ -n "$testcontainers_version" ]; then
    jar_path="$(
      find "$HOME/.gradle/caches/modules-2/files-2.1/org.testcontainers/testcontainers/$testcontainers_version" \
        -name "testcontainers-$testcontainers_version.jar" \
        2>/dev/null \
        | head -n 1
    )"

    if [ -n "$jar_path" ] && command -v javap >/dev/null 2>&1; then
      javap -classpath "$jar_path" -verbose org.testcontainers.utility.RyukContainer 2>/dev/null \
        | sed -n 's/.*\/\/ \(testcontainers\/ryuk:[^[:space:]]*\).*/\1/p' \
        | head -n 1
      return
    fi
  fi

  sed -n 's/^\(testcontainers\/ryuk:[^[:space:]]*\).*/\1/p' "$OUTPUT_FILE" 2>/dev/null | head -n 1
}

find_testcontainer_images >> "$TMP_FILE"

ryuk_image="$(find_ryuk_image)"
if [ -n "$ryuk_image" ]; then
  printf '%s\n' "$ryuk_image" >> "$TMP_FILE"
fi

sort -u "$TMP_FILE" > "$OUTPUT_FILE"
printf 'Updated %s with %s Docker images.\n' "$OUTPUT_FILE" "$(wc -l < "$OUTPUT_FILE" | tr -d ' ')"
