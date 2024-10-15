# 指定包含 .env 文件的目录
env_dir='.'

# 遍历目录中的所有 .env 文件
for file in "$env_dir"/*.env; do
  # 检查是否找到 .env 文件
  if [[ -f "$file" ]]; then
    echo "Processing $file..."
    # 执行脚本
    sh scripts/check-env-filter.sh env-filter "$file"
  else
    echo "No .env files found in the directory."
  fi
done

echo "Processing .env"
sh scripts/check-env-filter.sh env-filter ./server/src/test/resources/.env