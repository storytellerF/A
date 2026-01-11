#!/usr/bin/env bash
set -euo pipefail
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
# ===== 模拟环境变量 =====
export koyeb=true

# ===== 定义模板函数 =====
envAndArg() {
    replace_string_1=""

    while IFS= read -r key; do
        # 跳过空行
        if [[ -n "$key" ]]; then
            clean_key=$(echo "$key" | tr -d '\r')
            # 拼接 ARG 和 ENV 语句，替换 #1
            replace_string_1+="ARG $clean_key${newline}"
            replace_string_1+="ENV $clean_key=\$$clean_key${newline}"
        fi
    done < "koyeb-env.filter"
    echo $replace_string_1
}
saveEnvTofile() {
    replace_string_2="COPY <<EOF ./deploy/\${FLAVOR}.env\n"

    while IFS= read -r key; do
        # 跳过空行
        if [[ -n "$key" ]]; then
            clean_key=$(echo "$key" | tr -d '\r')
            replace_string_2+="$clean_key=\${$clean_key}${newline}"
        fi
    done < "koyeb-env.filter"

    # 完成 COPY 块的 EOF 部分
    replace_string_2+="EOF\nRUN mkdir -p build/envs \&\& cp ./deploy/\${FLAVOR}.env ./build/envs/.env"

    echo $replace_string_2
}
args() {
    replace_string_3=""
    while IFS= read -r key; do
        # 跳过空行
        if [[ -n "$key" ]]; then
            clean_key=$(echo "$key" | tr -d '\r')
            replace_string_3+="ARG $clean_key${newline}"
        fi
    done < "koyeb-env.filter"
    echo $replace_string_3
}

# ===== 预先执行函数并保存结果到变量 =====
fun_envAndArg="$(envAndArg)"
fun_saveEnvTofile="$(saveEnvTofile)"
fun_args="$(args)"

safe_fun_envAndArg=$(printf '%s' "$fun_envAndArg" | sed 's/&/\\&/g')
safe_fun_saveEnvTofile=$(printf '%s' "$fun_saveEnvTofile" | sed 's/&/\\&/g')
safe_fun_args=$(printf '%s' "$fun_args" | sed 's/&/\\&/g')
infile="$1"
outfile="$2"
cp "$infile" "$outfile"

# 循环解析直到没有标签
while grep -qE '#(startif|if|fun\(|else|endif)' "$outfile"; do
    awk \
        -v koyeb="${koyeb:-}" \
        -v fun_envAndArg="$safe_fun_envAndArg" \
        -v fun_args="$safe_fun_args" \
        -v fun_saveEnvTofile="$safe_fun_saveEnvTofile" '
    BEGIN { inside_cond=0; keep=0 }

    # 匹配 #startif($var)
    /^#startif\(\$[a-zA-Z0-9_]+\)/ {
        inside_cond=1
        varname=$0
        sub(/^#startif\(\$/, "", varname)
        sub(/\).*$/, "", varname)
        keep = (ENVIRON[varname] != "")
        next
    }

    # 匹配 #else
    /^#else/ {
        if (inside_cond) keep = !keep
        next
    }

    # 匹配 #endif
    /^#endif/ {
        inside_cond=0
        keep=0
        next
    }

    # 匹配单行 #if($var) xxx
    /^#if\(\$[a-zA-Z0-9_]+\)/ {
        varname=$0
        sub(/^#if\(\$/, "", varname)
        sub(/\).*/, "", varname)
        if (ENVIRON[varname] != "") {
            sub(/^#if\(\$[^\)]+\)[ \t]*/, "", $0)
            print $0
        }
        next
    }

    # 处理函数调用 #fun(name)
    {
        if (match($0, /#fun\(([a-zA-Z0-9_]+)\)/, m)) {
            if (m[1] == "envAndArg") {
                gsub(/#fun\([^\)]+\)/, fun_envAndArg)
            } else if (m[1] == "saveEnvTofile") {
                gsub(/#fun\([^\)]+\)/, fun_saveEnvTofile)
            }
        }
    }

    {
        if (!inside_cond || keep) print
    }
    ' "$outfile" > "$outfile.tmp"
    mv "$outfile.tmp" "$outfile"
done