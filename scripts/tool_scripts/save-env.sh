#!/bin/bash
OUTPUT_FILE="$FLAVOR.env"

# 定义 env-filter 文件名
env_filter_file="env-filter"

# 创建或清空 .env 文件
> $OUTPUT_FILE

# 遍历 env-filter 文件中的每一行
while IFS= read -r key; do
  # 去除可能的 \r 符号（处理 Windows 换行符）
  key=$(echo "$key" | tr -d '\r')
  upper_key=$(echo "$key" | tr '[:lower:]' '[:upper:]')

  # 获取对应的环境变量值
  value="${!key}"
  # 对反斜杠进行转义
  value=$(echo "$value" | sed 's/\\/\\\\/g')

  # 如果值中包含空格，则用引号包裹
  if echo "$value" | grep -q ' '; then
      echo "$upper_key=\"$value\"" >> "$OUTPUT_FILE"
  else
      echo "$upper_key=$value" >> "$OUTPUT_FILE"
  fi
done < "$env_filter_file"

echo "$FLAVOR.env 文件已生成。"
