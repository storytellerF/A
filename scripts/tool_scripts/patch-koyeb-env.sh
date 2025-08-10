#!/bin/bash
set -e
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

# 文件路径
dockerfile_template=$1
if [ -z "$dockerfile_template" ]; then
  echo "Error: dockerfile_template is not specified."
  exit 1
fi
dockerfile_output="${dockerfile_template}.patched"

# 初始化两个空字符串来存储结果
replace_string_1=""
replace_string_2="COPY <<EOF ./\${FLAVOR}.env\n"
replace_string_3=""

while IFS= read -r key; do
    # 跳过空行
    if [[ -n "$key" ]]; then
        clean_key=$(echo "$key" | tr -d '\r')
        # 拼接 ARG 和 ENV 语句，替换 #1
        replace_string_1+="ARG $clean_key${newline}"
        replace_string_1+="ENV $clean_key=\$$clean_key${newline}"

        # 拼接 COPY <<EOF 块，替换 #2
        replace_string_2+="$clean_key=\${$clean_key}${newline}"

        replace_string_3+="ARG $clean_key${newline}"
    fi
done < "koyeb-env.filter"

# 完成 COPY 块的 EOF 部分
replace_string_2+="EOF\nRUN mkdir -p build/envs \&\& cp ./\${FLAVOR}.env ./build/envs/.env"

# 使用 sed 替换 dockerfile 模板中的 #1 和 #2
sed -e "s|^#1.*|$replace_string_1|" \
  -e "s|^#2.*|$replace_string_2|" \
  -e "s|^#3.*|$replace_string_3|" "$dockerfile_template" > "$dockerfile_output"

sed -i.bak '/#\^1/,/#!1/c\
#patched
' "$dockerfile_output"

if [[ "$(uname -s)" =~ Darwin ]]; then
  sed -i '' 's/#if //g' "$dockerfile_output"
else
  sed -i 's/#if //g' "$dockerfile_output"
fi

echo "Dockerfile 已生成: $dockerfile_output"
