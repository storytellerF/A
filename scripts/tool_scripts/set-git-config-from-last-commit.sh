#!/usr/bin/env bash

# 获取最后一个 commit 的作者名称和邮箱
AUTHOR_NAME=$(git log -1 --format='%an')
AUTHOR_EMAIL=$(git log -1 --format='%ae')

if [ -z "$AUTHOR_NAME" ]; then
    echo "Failed to get author name from the last commit."
    exit 1
fi

if [ -z "$AUTHOR_EMAIL" ]; then
    echo "Failed to get author email from the last commit."
    exit 1
fi

# 设置到 git config 中
git config user.name "$AUTHOR_NAME"
git config user.email "$AUTHOR_EMAIL"

echo "Git config updated successfully:"
echo "user.name = $AUTHOR_NAME"
echo "user.email = $AUTHOR_EMAIL"
