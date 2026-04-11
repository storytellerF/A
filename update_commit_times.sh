#!/bin/bash

# 脚本功能：修改所有未推送的 commit 的时间为当前时间之后，每个 commit 之间间隔随机时间

# 颜色定义
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m" # No Color

# 获取当前目录的绝对路径
CURRENT_DIR=$(pwd)
# 临时文件，用于存储当前时间戳
TIMESTAMP_FILE="$CURRENT_DIR/.timestamp.tmp"

# 检查是否在 git 仓库中
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    echo -e "${RED}错误：当前目录不是 git 仓库${NC}"
    exit 1
fi

# 检查是否有未推送的提交
REMOTE_BRANCH=$(git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null || echo "")
if [ -z "$REMOTE_BRANCH" ]; then
    echo -e "${RED}错误：当前分支没有设置上游分支，无法获取未提交的 commit 数量${NC}"
    exit 1
fi

# 获取远程分支的最新提交
git fetch --quiet
BASE_COMMIT=$(git merge-base HEAD "$REMOTE_BRANCH")

# 计算未推送的 commit 数量
UNPUSHED_COMMITS=$(git log --oneline $BASE_COMMIT..HEAD | wc -l)
if [ "$UNPUSHED_COMMITS" -eq 0 ]; then
    echo -e "${GREEN}信息：没有未推送的提交${NC}"
    exit 0
fi

# 检查是否有未暂存的更改
if git status --porcelain | grep -q "^[A-Z]"; then
    echo -e "${RED}错误：当前工作目录有未暂存的更改，请先提交或 stash 这些更改${NC}"
    exit 1
fi

echo -e "${GREEN}信息：发现 $UNPUSHED_COMMITS 个未推送的提交，将重写从 $BASE_COMMIT 到当前 HEAD 的提交历史${NC}"


# 初始化时间戳文件，设置为当前时间
echo $(date +%s) > "$TIMESTAMP_FILE"

echo -e "${GREEN}开始重写提交历史...${NC}"

# 设置 filter-branch 警告抑制
export FILTER_BRANCH_SQUELCH_WARNING=1

# 定义环境过滤器命令
FILTER_CMD='
    TS_FILE="'"$TIMESTAMP_FILE"'"
    CURRENT_TIMESTAMP=$(cat "$TS_FILE")
    RANDOM_INTERVAL=$((($$ + $(date +%s)) % 3600 + 1))
    NEW_TIMESTAMP=$((CURRENT_TIMESTAMP + RANDOM_INTERVAL))
    echo $NEW_TIMESTAMP > "$TS_FILE"
    export GIT_AUTHOR_DATE="$NEW_TIMESTAMP +0000"
    export GIT_COMMITTER_DATE="$NEW_TIMESTAMP +0000"
    echo "修改提交 $GIT_COMMIT 的时间为 $(date -d @$NEW_TIMESTAMP 2>/dev/null || date)"
'

# 使用 git filter-branch 重写历史
# 只重写从 BASE_COMMIT 到当前 HEAD 的历史（未推送的提交）
git filter-branch -f --env-filter "$FILTER_CMD" $BASE_COMMIT..HEAD

# 清理临时文件
rm -f "$TIMESTAMP_FILE"

echo -e "${GREEN}提交时间修改完成！${NC}"
echo -e "${YELLOW}注意：由于修改了提交历史，你需要使用 git push --force 推送到远程仓库${NC}"
