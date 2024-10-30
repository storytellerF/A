set -e
FLAVOR=$1

FLAVOR=$1
if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

sh scripts/tool_scripts/save-env.sh $FLAVOR

sh scripts/tool_scripts/modify-flavor.sh $FLAVOR true

sh gradlew composeApp:build

mkdir -p "build/outputs/apk/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"
