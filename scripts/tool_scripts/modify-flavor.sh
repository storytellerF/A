FLAVOR=$1
IS_PROD=$2

if [ -z "$FLAVOR" ] || [ -z "$IS_PROD" ]; then
  echo "FLAVOR and IS_PROD must be set"
  exet 1
fi

if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' "s/buildkonfig.flavor=.*/buildkonfig.flavor=${FLAVOR}/" gradle.properties && \
      sed -i '' "s/server.prod=false/server.prod=${IS_PROD}/" gradle.properties
else
    sed -i "s/buildkonfig.flavor=.*/buildkonfig.flavor=${FLAVOR}/" gradle.properties && \
      sed -i "s/server.prod=false/server.prod=${IS_PROD}/" gradle.properties
fi
