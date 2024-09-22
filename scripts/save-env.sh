# 读取允许的环境变量键，存入一个数组
allowed_keys=$(cat mini.env-filter)
# 输出当前环境变量，并只保留在 env-filter 中定义的键
env | while IFS='=' read -r key value; do
    # 将 key 转换为大写
    upper_key=$(echo "$key" | tr '[:lower:]' '[:upper:]')
    # 检查该 key 是否在允许的列表中
    if echo "$allowed_keys" | grep -qx "$upper_key"; then
        # 对反斜杠进行转义
        value=$(echo "$value" | sed 's/\\/\\\\/g')

        # 如果值中包含空格，则用引号包裹
        if echo "$value" | grep -q ' '; then
            echo "$upper_key=\"$value\""
        else
            echo "$upper_key=$value"
        fi
    fi
done > generated-mini.env