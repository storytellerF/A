set -e
FLAVOR=$1
IS_PROD=$2

if [ -z "$FLAVOR" ] || [ -z "$IS_PROD" ]; then
  echo "FLAVOR and IS_PROD must be set"
  exet 1
fi

sh scripts/tool_scripts/modify-flavor.sh $FLAVOR $IS_PROD

sh scripts/build_scripts/build-server-on-condition.sh