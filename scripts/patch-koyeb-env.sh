#!/bin/bash

# 文件路径
env_filter_file="env-filter"
dockerfile_template="deploy/Dockerfile.koyeb_template"
dockerfile_output="deploy/Dockerfile.koyeb"

# 初始化两个空字符串来存储结果
replace_string_1=""
replace_string_2="COPY <<EOF ./generated-mini.env\n"

# 读取 env-filter 文件中的 keys，生成 ARG 和 ENV 语句，同时生成 COPY 环境变量
while IFS= read -r key; do
    # 跳过空行
    if [[ -n "$key" ]]; then
        # 拼接 ARG 和 ENV 语句，替换 #1
        replace_string_1+="ARG $key\n"
        replace_string_1+="ENV $key=\$$key\n"

        # 拼接 COPY <<EOF 块，替换 #2
        replace_string_2+="$key=\${$key}\n"
    fi
done < "$env_filter_file"

# 完成 COPY 块的 EOF 部分
replace_string_2+="EOF\n"

# 使用 sed 替换 dockerfile 模板中的 #1 和 #2
sed -e "s|#1|$replace_string_1|" -e "s|#2|$replace_string_2|" "$dockerfile_template" > "$dockerfile_output"

echo "Dockerfile 已生成: $dockerfile_output"
