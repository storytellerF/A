#!/usr/bin/env bash

# remove_strings.sh
# Usage: ./remove_strings.sh <keys_file>
# Reads each line from <keys_file> and removes the <string name="key">...</string>
# entry from any strings.xml file in the current directory tree.

set -e

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <keys_file>"
  exit 1
fi

keys_file="$1"

# Determine sed inline edit syntax depending on OS
if sed --version >/dev/null 2>&1; then
  # GNU sed (Linux)
  SED_INLINE=("sed" "-i")
else
  # BSD sed (macOS)
  SED_INLINE=("sed" "-i" "")
fi

# Find strings.xml files safely
while IFS= read -r -d '' xml_file; do
  echo "Processing $xml_file..."

  while IFS= read -r key; do
    [[ -z "$key" ]] && continue
    key=$(echo "$key" | tr -d '\r')

    esc_key=$(printf '%s' "$key" | sed 's/[\/&]/\\&/g')

    # Only apply delete if string exists
    if grep -q "<string[[:space:]]*name[[:space:]]*=\"${esc_key}\"" "$xml_file"; then
      "${SED_INLINE[@]}" "/<string[[:space:]]\\+name[[:space:]]*=[[:space:]]*\"$esc_key\"/d" "$xml_file"
      echo "  Removed key: $key"
    fi
  done < "$keys_file"

done < <(find . -name "strings.xml" -print0)

echo "Finished removing keys from all found strings.xml files."
