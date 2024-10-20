# 检查是否传递了命令作为参数
if [ -z "$1" ]; then
  echo "Error: No command provided."
  exit 1
fi

# 将所有的参数当做要执行的命令
COMMAND="$@"

# 无限循环执行命令直到成功
while true; do
  echo "Executing: $COMMAND"

  # 执行命令
  $COMMAND

  result=$?
  # 检查命令执行是否成功（返回码为0表示成功）
  if [ $result -eq 0 ]; then
    echo "Command succeeded."
    break
  else
    echo "Command failed[$result]. Retrying..."
    sleep 1  # 等待一秒再重试，避免过快的重试
  fi
done
