#!/bin/bash

# 查找项目中未被使用的文案
# 该脚本会扫描项目的字符串资源文件，并检查哪些字符串没有在代码中被引用

echo "🔍 开始查找未使用的文案..."

# 定义资源文件路径
RESOURCES_DIRS=(
    "app/composeApp/src/commonMain/composeResources"
    "app/core/src/commonMain/composeResources"
    "panel/composeApp/src/commonMain/composeResources"
)

# 创建临时文件存储所有字符串名称
ALL_STRINGS_FILE="/tmp/all_strings_$$.txt"
UNUSED_STRINGS_FILE="/tmp/unused_strings_$$.txt"

# 清理函数
cleanup() {
    rm -f "$ALL_STRINGS_FILE" "$UNUSED_STRINGS_FILE"
}
trap cleanup EXIT

echo "📝 收集所有字符串资源..."

# 清空文件
> "$ALL_STRINGS_FILE"

# 从XML资源文件中提取所有字符串名称
for resource_dir in "${RESOURCES_DIRS[@]}"; do
    if [[ -d "$resource_dir" ]]; then
        find "$resource_dir" -name "strings.xml" | while read -r xml_file; do
            echo "Processing $xml_file"
            # 提取<string>标签中的name属性
            grep -o '<string name="[^"]*"' "$xml_file" | sed 's/<string name="//' | sed 's/"//' >> "$ALL_STRINGS_FILE"
        done
    fi
done

# 等待所有后台进程完成
wait

# 如果没有找到任何字符串资源，直接退出
if [[ ! -s "$ALL_STRINGS_FILE" ]]; then
    echo "⚠️ 未找到任何字符串资源文件"
    exit 0
fi

# 去重
sort "$ALL_STRINGS_FILE" | uniq > "${ALL_STRINGS_FILE}.tmp" && mv "${ALL_STRINGS_FILE}.tmp" "$ALL_STRINGS_FILE"

echo "🔎 检查字符串使用情况..."

# 复制所有字符串到未使用文件
cp "$ALL_STRINGS_FILE" "$UNUSED_STRINGS_FILE"

# 获取项目根目录
PROJECT_ROOT="."

# 创建一个临时文件来存储已使用的字符串
USED_STRINGS_FILE="/tmp/used_strings_$$.txt"
> "$USED_STRINGS_FILE"

# 首先找出所有被使用的字符串，这样更高效
echo "查找所有被使用的字符串..."
# 查找 stringResource(Res.string.xxx) 和 getString(Res.string.xxx) 的使用
# 先获取所有包含 Res.string 的文件，然后提取字符串名称
grep -r -o --include="*.kt" --exclude-dir=".git" --exclude-dir="build" \
    --exclude-dir="node_modules" --exclude-dir=".gradle" --exclude-dir="generated" \
    "Res\.string\.[a-zA-Z0-9_]*" "$PROJECT_ROOT" | \
    awk -F 'Res\\.string\\.' '{print $2}' | \
    grep -v "^$" | sort | uniq > "$USED_STRINGS_FILE"

echo "比较所有字符串和已使用字符串..."

# 从所有字符串中移除已使用的字符串
comm -23 <(sort "$ALL_STRINGS_FILE") <(sort "$USED_STRINGS_FILE") > "$UNUSED_STRINGS_FILE"

# 显示结果
if [[ -f "$UNUSED_STRINGS_FILE" ]]; then
    UNUSED_COUNT=$(wc -l < "$UNUSED_STRINGS_FILE" | tr -d ' ')
else
    UNUSED_COUNT=0
fi

if [[ -f "$ALL_STRINGS_FILE" ]]; then
    TOTAL_COUNT=$(wc -l < "$ALL_STRINGS_FILE" | tr -d ' ')
else
    TOTAL_COUNT=0
fi

echo "📊 分析完成!"
echo "总字符串数: $TOTAL_COUNT"
echo "未使用字符串数: $UNUSED_COUNT"

if [[ $UNUSED_COUNT -gt 0 ]]; then
    echo ""
    echo "❌ 以下字符串可能未被使用:"
    if [[ -s "$UNUSED_STRINGS_FILE" ]]; then
        head -50 "$UNUSED_STRINGS_FILE"  # 只显示前50个未使用的字符串
        if [[ $(wc -l < "$UNUSED_STRINGS_FILE") -gt 50 ]]; then
            echo "... (还有 $(( $(wc -l < "$UNUSED_STRINGS_FILE") - 50 )) 个未显示)"
        fi
    fi
    echo ""
    echo "💡 建议: 检查以上字符串是否确实不再需要，可以考虑删除以减小应用体积"
else
    echo "✅ 所有字符串都被使用了!"
fi

echo "✅ 查找完成"