#!/bin/sh

# 把所有的文件和目录的所有者改为指定用户
# 用法: chown-user.sh <用户名> <目录路径>
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <username> <directory_path>"
    exit 1
fi
USERNAME=$1
DIRECTORY_PATH=$2
if [ ! -d "$DIRECTORY_PATH" ]; then
    echo "Error: Directory $DIRECTORY_PATH does not exist."
    exit 1
fi
chown -R "$USERNAME":"$USERNAME" "$DIRECTORY_PATH"
echo "Changed ownership of all files and directories in $DIRECTORY_PATH to user $USERNAME."