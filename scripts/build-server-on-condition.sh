set -e

# 假设 IS_HOST 和 BUILD_ON_HOST 变量已经定义，可以通过环境变量或者脚本参数传入
# 示例： export IS_HOST=true; export BUILD_ON_HOST=true

# 判断 IS_HOST 和 BUILD_ON_HOST 是否设置
if [ -z "$IS_HOST" ] || [ -z "$BUILD_ON_HOST" ]; then
  echo "Error: IS_HOST and BUILD_ON_HOST must be set."
  exit 1
fi

# 根据 BUILD_ON_HOST 和 IS_HOST 的值进行判断
if [ "$BUILD_ON_HOST" = "true" ]; then
  # 如果 BUILD_ON_HOST 是 true，判断 IS_HOST 是否为 true
  if [ "$IS_HOST" = "true" ]; then
    echo "Both BUILD_ON_HOST and IS_HOST are true, proceeding with build..."
  else
    echo "Error: BUILD_ON_HOST is true but IS_HOST is not true. Cannot proceed with build."
    exit 0
  fi
else
  # 如果 BUILD_ON_HOST 不是 true，判断 IS_HOST 是否为 true
  if [ "$IS_HOST" != "true" ]; then
    echo "BUILD_ON_HOST is not true and IS_HOST is not true, proceeding with build..."
  else
    echo "Error: BUILD_ON_HOST is not true but IS_HOST is true. Cannot proceed with build."
    exit 0
  fi
fi

[ -e deploy/build ] || mkdir deploy/build
sh scripts/build-server.sh && sh scripts/build-cli.sh
