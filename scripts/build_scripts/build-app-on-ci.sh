#!/bin/bash
set -e
URL=$1
FLAVOR=$2
FLAVOR="g-$FLAVOR"
IS_PROD="true"

# 自动根据系统环境设置换行符格式
use_windows_newline=false

# 检测操作系统
if [[ "$(uname -s)" =~ MINGW|CYGWIN|MSYS ]]; then
  use_windows_newline=true
fi

# 设置换行符变量
newline="\n"

if [ "$use_windows_newline" = true ]; then
  newline="\r\n"
fi

cat > "./$FLAVOR.env" <<EOF
SERVER_URL=${URL}${newline}
WS_SERVER_URL=${URL}${newline}
EOF

TEMP_FILE=./temp

# Pipe the JSON string into jq
echo "$SECRETS_CONTEXT" | 
# Convert JSON object into an array of key-value pairs
jq -r 'to_entries | 
# Map over each key-value pair
.[] | 
# Format each pair as "KEY=VALUE" and append it all to the environment file
"\(.key)=\(.value)"' >> $TEMP_FILE

while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    export "$key"="$value"
done < $TEMP_FILE

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$IS_PROD"

./gradlew composeApp:build

mkdir -p "build/outputs/apk/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"
