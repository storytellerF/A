#!/bin/bash

# 用于存储已加载的文件路径，防止重复加载
declare -A loaded_files

# 递归加载 .env 文件的函数
load_env_file() {
    local env_file=$1

    # 检查文件是否已加载
    if [[ -n "${loaded_files[$env_file]}" ]]; then
        return  # 已加载，跳过
    fi

    # 检查文件是否存在
    if [[ ! -f "$env_file" ]]; then
        echo "错误: 文件 '$env_file' 不存在!"
        exit 1
    fi

    # 标记文件为已加载
    loaded_files["$env_file"]=1

    # 输出当前文件，添加到最终的 env_files 列表中
    echo "加载文件: $env_file"

    # 解析 .env 文件中的 inherent 字段
    inherent_files=$(grep -E '^INHERENT=' "$env_file" | cut -d'=' -f2)

    # 如果有 inherent 字段，递归加载这些文件
    if [[ -n "$inherent_files" ]]; then
        IFS=',' read -r -a files <<< "$inherent_files"
        for file in "${files[@]}"; do
            load_env_file "$file"
        done
    fi

    # 将当前的 .env 文件添加到最终的 env_files 列表中
    env_files+=("$env_file")
}

# 接收 .env 文件路径作为参数
env_file=$1

# 检查文件是否传递
if [[ -z "$env_file" ]]; then
    echo "错误: 请提供 .env 文件路径!"
    exit 1
fi

# 初始化存储最终文件路径的数组
env_files=()

# 加载初始的 .env 文件
load_env_file "$env_file"

# 输出 Docker Compose 命令
compose_command="docker-compose"
for env in "${env_files[@]}"; do
    compose_command+=" --env-file $env"
done

# 输出最终的命令
echo "生成的 Docker Compose 命令："
echo "$compose_command up"
