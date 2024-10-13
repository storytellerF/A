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
        clean_key=$(echo "$key" | tr -d '\r')
        # 拼接 ARG 和 ENV 语句，替换 #1
        replace_string_1+="ARG $clean_key${newline}"
        replace_string_1+="ENV $clean_key=\$$clean_key${newline}"

        # 拼接 COPY <<EOF 块，替换 #2
        replace_string_2+="$clean_key=\${$clean_key}${newline}"
    fi
done < "$env_filter_file"

# 完成 COPY 块的 EOF 部分
replace_string_2+="EOF\n"

# 使用 sed 替换 dockerfile 模板中的 #1 和 #2
sed -e "s|#1|$replace_string_1|" -e "s|#2|$replace_string_2|" "$dockerfile_template" > "$dockerfile_output"

echo "Dockerfile 已生成: $dockerfile_output"
