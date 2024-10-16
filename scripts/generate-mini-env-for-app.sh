URL=$1
FLAVOR="generated-mini"
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

cat > ./generated-mini.env <<EOF
SERVER_URL=${URL}${newline}
WS_SERVER_URL=${URL}${newline}
EOF

if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' "s/buildkonfig.flavor=dev/buildkonfig.flavor=${FLAVOR}/" gradle.properties && \
      sed -i '' "s/server.prod=false/server.prod=${IS_PROD}/" gradle.properties
else
    sed -i "s/buildkonfig.flavor=dev/buildkonfig.flavor=${FLAVOR}/" gradle.properties && \
      sed -i "s/server.prod=false/server.prod=${IS_PROD}/" gradle.properties
fi
