set -e

sh scripts/tool_scripts/save-env.sh $FLAVOR

sh scripts/tool_scripts/modify-flavor.sh $FLAVOR true

sh gradlew composeApp:build

mkdir -p "build/outputs/apk/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"
