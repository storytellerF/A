#!/bin/bash
set -e
FLAVOR=$1
URL=$2
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
SERVER_URL=https://${URL}${newline}
WS_SERVER_URL=wss://${URL}${newline}
EOF

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$IS_PROD"

TEMP_FILE=./temp
# 解析SECRETS_CONTEXT 到文件
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
#从文件写入环境变量
while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    # Check if key starts with "storyteller_f" (case-insensitive)
    [[ ! "$key" =~ ^[Ss][Tt][Oo][Rr][Yy][Tt][Ee][Ll][Ll][Ee][Rr]_[Ff] ]] && continue

    export "$key"="$value"
done < $TEMP_FILE

./gradlew composeApp:build
./gradlew composeApp:packageReleaseMsi && ./gradlew composeApp:packageReleaseDeb && ./gradlew composeApp:packageReleaseDmg

mkdir -p "build/outputs/apk/release"
mkdir -p "build/outputs/pkg/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"
mv composeApp/build/compose/binaries/main-release/msi/*.msi "build/outputs/pkg/release/$FLAVOR.msi"
