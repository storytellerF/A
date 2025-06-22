#!/bin/bash
set -e
FLAVOR=$1
URL=$2
TARGET=$3
BUILD_TYPE="prod"

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

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$BUILD_TYPE"

echo "$SECRETS_CONTEXT" | jq -r 'to_entries | .[] | "\(.key)=\(.value)"' | while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    # Check if key starts with "storyteller_f" (case-insensitive)
    [[ ! "$key" =~ ^[Ss][Tt][Oo][Rr][Yy][Tt][Ee][Ll][Ll][Ee][Rr]_[Ff] ]] && continue

    echo "export $key=$value"
done > ./secrets_env.sh

# 然后在 shell 中执行：
. ./secrets_env.sh

case "$TARGET" in
    android)
        echo "Running Android-specific command..."
        # 在这里添加 Android 相关命令
        ./gradlew app:composeApp:assembleRelease
        mkdir -p "build/outputs/apk/release"
        mv app/composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"
        ;;
    desktop-msi)
        echo "Running DesktopMsi-specific command..."
        ./gradlew app:composeApp:packageReleaseMsi
        mkdir -p "build/outputs/pkg/release"
        mv app/composeApp/build/compose/binaries/main-release/msi/*.msi "build/outputs/pkg/release/$FLAVOR.msi"
        ;;
    desktop-deb)
        echo "Running DesktopDeb-specific command..."
        ./gradlew app:composeApp:packageReleaseDeb
        mkdir -p "build/outputs/pkg/release"
        mv app/composeApp/build/compose/binaries/main-release/deb/*.deb "build/outputs/pkg/release/$FLAVOR.deb"
        ;;
    desktop-dmg)
        echo "Running DesktopDmg-specific command..."
        ./gradlew app:composeApp:packageReleaseDmg
        mkdir -p "build/outputs/pkg/release"
        mv app/composeApp/build/compose/binaries/main-release/dmg/*.dmg "build/outputs/pkg/release/$FLAVOR.dmg"
        ;;
    *)
        echo "Invalid target: $TARGET. Use 'android' or 'desktop-*'."
        ;;
esac
