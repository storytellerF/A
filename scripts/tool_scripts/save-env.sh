#!/bin/bash
OUTPUT_FILE="deploy/$FLAVOR.env"

truncate -s 0 "$OUTPUT_FILE"

while IFS= read -r key; do
  # 去除可能的 \r 符号（处理 Windows 换行符）
  key=$(echo "$key" | tr -d '\r')
  upper_key=$(echo "$key" | tr '[:lower:]' '[:upper:]')

  # 获取对应的环境变量值 bash 特有
  value="${!key}"
  # 对反斜杠进行转义
  value="${value//\\/\\\\}"

  # 如果值中包含空格，则用引号包裹
  if echo "$value" | grep -q ' '; then
      echo "$upper_key=\"$value\"" >> "$OUTPUT_FILE"
  else
      echo "$upper_key=$value" >> "$OUTPUT_FILE"
  fi
done < "env.filter"

echo "deploy/$FLAVOR.env 文件已生成。"