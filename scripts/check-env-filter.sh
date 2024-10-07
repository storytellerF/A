#!/bin/bash
set -e
# 文件路径
env_file=$2
env_filter_file=$1

# 读取 env-filter 文件中的 keys，存储到数组中
env_filter_keys=()
while IFS= read -r line; do
    # 跳过空行
    if [[ -n "$line" ]]; then
        env_filter_keys+=("$line")
    fi
done < "$env_filter_file"

# 读取 env 文件中的 keys
env_keys=()
while IFS='=' read -r key value; do
    # 跳过空行和以 # 开头的注释行
    if [[ -n "$key" && "$key" != \#* ]]; then
        env_keys+=("$key")
    fi
done < "$env_file"
# 验证 env_keys 和 env_filter_keys 是否按顺序匹配
if [ ${#env_keys[@]} -ne ${#env_filter_keys[@]} ]; then
    echo "env 和 env-filter 文件中的变量数量不匹配!"
    exit 1
fi

# 按行对比
for i in "${!env_keys[@]}"; do
    if [ "${env_keys[$i]}" != "${env_filter_keys[$i]}" ]; then
        echo "不匹配: 第 $((i + 1)) 行的 env 变量 '${env_keys[$i]}' 不匹配 env-filter 变量 '${env_filter_keys[$i]}'"
        exit 1
    fi
done

echo "环境变量与 env-filter 中的定义完全按顺序匹配!"
