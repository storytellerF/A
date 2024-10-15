URL=$1
FLAVOR="generated-mini"
IS_PROD="true"

cat > ./generated-mini.env <<EOF
SERVER_URL=${URL}
WS_SERVER_URL=${URL}
EOF

sed -i '' "s/buildkonfig.flavor=dev/buildkonfig.flavor=${FLAVOR}/" gradle.properties && \
  sed -i '' "s/server.prod=false/server.prod=${IS_PROD}/" gradle.properties