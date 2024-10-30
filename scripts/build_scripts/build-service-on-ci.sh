set -e
FLAVOR=$1

FLAVOR=$1
if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

sh save-env.sh $FLAVOR

sh modify-flavor.sh $FLAVOR true

sh gradlew build

mkdir -p "build/outputs/apk/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"
