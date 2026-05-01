#!/bin/bash
set -e

mkdir -p ~/.gradle
echo "gpr.user=$ORG_GRADLE_PROJECT_gpr_user" >> ~/.gradle/gradle.properties
echo "gpr.key=$ORG_GRADLE_PROJECT_gpr_key" >> ~/.gradle/gradle.properties

FLAVOR=$1
URL=$2
TARGET=$3
BUILD_TYPE="prod"

cat > "./deploy/${FLAVOR}.env" <<EOF
SERVER_URL=https://${URL}
WS_SERVER_URL=wss://${URL}
EOF

echo "$SECRETS_CONTEXT" | jq -r 'to_entries | .[] | "\(.key)=\(.value)"' | while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"

    echo "export $key=$value"
done > ./secrets_env.sh

# 然后在 shell 中执行：
. ./secrets_env.sh
mkdir -p app/config

case "$TARGET" in
    android)
        echo "Running Android-specific command..."
        # 在这里添加 Android 相关命令
        ./gradlew app:android:assembleRelease --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/apk/release"
        for f in app/android/build/outputs/apk/release/*.apk; do
            cp "$f" "build/outputs/apk/release/app-${FLAVOR}_$(basename "$f")"
        done
        ;;
    desktop-msi)
        echo "Running DesktopMsi-specific command..."
        ./gradlew app:composeApp:packageReleaseMsi --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/pkg/release"
        mv app/composeApp/build/compose/binaries/main-release/msi/*.msi "build/outputs/pkg/release/app-$FLAVOR.msi"
        ;;
    desktop-deb)
        echo "Running DesktopDeb-specific command..."
        ./gradlew app:composeApp:packageReleaseDeb --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/pkg/release"
        mv app/composeApp/build/compose/binaries/main-release/deb/*.deb "build/outputs/pkg/release/app-$FLAVOR.deb"
        ;;
    desktop-dmg)
        echo "Running DesktopDmg-specific command..."
        ./gradlew app:composeApp:packageReleaseDmg --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/pkg/release"
        mv app/composeApp/build/compose/binaries/main-release/dmg/*.dmg "build/outputs/pkg/release/app-$FLAVOR.dmg"
        ;;
    android-panel)
        echo "Running AndroidPanel-specific command..."
        # 在这里添加 AndroidPanel 相关命令
        ./gradlew panel:android:assembleRelease --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/apk/release"
        for f in panel/android/build/outputs/apk/release/*.apk; do
            cp "$f" "build/outputs/apk/release/panel-${FLAVOR}_$(basename "$f")"
        done
        ;;
    desktop-msi-panel)
        echo "Running DesktopMsiPanel-specific command..."
        ./gradlew panel:composeApp:packageReleaseMsi --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/pkg/release"
        mv panel/composeApp/build/compose/binaries/main-release/msi/*.msi "build/outputs/pkg/release/panel-$FLAVOR-panel.msi"
        ;;
    desktop-deb-panel)
        echo "Running DesktopDebPanel-specific command..."
        ./gradlew panel:composeApp:packageReleaseDeb --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/pkg/release"
        mv panel/composeApp/build/compose/binaries/main-release/deb/*.deb "build/outputs/pkg/release/panel-$FLAVOR-panel.deb"
        ;;
    desktop-dmg-panel)
        echo "Running DesktopDmgPanel-specific command..."
        ./gradlew panel:composeApp:packageReleaseDmg --no-daemon -Pserver.flavor="$FLAVOR" -Pserver.buildType="$BUILD_TYPE"
        mkdir -p "build/outputs/pkg/release"
        mv panel/composeApp/build/compose/binaries/main-release/dmg/*.dmg "build/outputs/pkg/release/panel-$FLAVOR-panel.dmg"
        ;;
    *)
        echo "Invalid target: $TARGET. Use 'android' or 'desktop-*'."
        ;;
esac
