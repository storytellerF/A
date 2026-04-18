#!/bin/bash
# 自动检测并移除所有模块 build.gradle.kts 中未被使用的 implementation 依赖
# 用法：在项目根目录下运行本脚本

set -e

ROOT_DIR=$(pwd)
GRADLEW="$ROOT_DIR/gradlew"

echo "查找所有模块的 build.gradle.kts ..."
mapfile -t GRADLE_FILES < <(find "$ROOT_DIR" -type f -name 'build.gradle.kts' \
    ! -path "$ROOT_DIR/build.gradle.kts" \
    ! -path "*/bgscripts/*")

if [ ${#GRADLE_FILES[@]} -eq 0 ]; then
  echo "未找到任何模块的 build.gradle.kts 文件。"
  exit 0
fi


# 全局汇总数组
declare -a GLOBAL_REMOVED

for GRADLE_FILE in "${GRADLE_FILES[@]}"; do
  MODULE_DIR=$(dirname "$GRADLE_FILE")
  cd "$MODULE_DIR"
  echo -e "\n=============================="
  echo "模块: $MODULE_DIR"
  BACKUP_FILE="build.gradle.kts.bak.prune"
  TMP_FILE="build.gradle.kts.tmp.prune"

  cp build.gradle.kts "$BACKUP_FILE"
  echo "备份 build.gradle.kts 到 $BACKUP_FILE"

  mapfile -t lines < <(grep -n '^\s*implementation' build.gradle.kts)
  if [ ${#lines[@]} -eq 0 ]; then
    echo "未找到 implementation 依赖，跳过。"
    rm "$BACKUP_FILE"
    cd "$ROOT_DIR"
    continue
  fi

  REMOVABLE=()
  declare -a removable_lines
  for idx in "${!lines[@]}"; do
    entry="${lines[$idx]}"
    lineno=$(echo "$entry" | cut -d: -f1)
    dep=$(echo "$entry" | cut -d: -f2-)
    echo -e "\n尝试移除: $dep (第 $lineno 行)"

    cp build.gradle.kts "$TMP_FILE"
    sed -i "${lineno}s/^/\/\//" "$TMP_FILE"
    mv "$TMP_FILE" build.gradle.kts

    if "$GRADLEW" --quiet build > /dev/null 2>&1; then
      echo "可以移除: $dep"
      REMOVABLE+=("$dep")
      removable_lines+=("$lineno")
    else
      echo "不能移除: $dep，恢复..."
      cp "$BACKUP_FILE" build.gradle.kts
    fi
  done

  if [ ${#removable_lines[@]} -gt 0 ]; then
    cp "$BACKUP_FILE" build.gradle.kts
    for ((i=${#removable_lines[@]}-1; i>=0; i--)); do
      sed -i "${removable_lines[$i]}d" build.gradle.kts
    done
    echo -e "\n已自动移除以下 implementation 依赖："
    for dep in "${REMOVABLE[@]}"; do
      echo "$dep"
      GLOBAL_REMOVED+=("$MODULE_DIR: $dep")
    done
    rm "$BACKUP_FILE"
  else
    cp "$BACKUP_FILE" build.gradle.kts
    rm "$BACKUP_FILE"
    echo "没有可安全移除的 implementation 依赖。"
  fi
  cd "$ROOT_DIR"
done

# 汇总输出
if [ ${#GLOBAL_REMOVED[@]} -gt 0 ]; then
  echo -e "\n=============================="
  echo "汇总：所有被移除的 implementation 依赖："
  for item in "${GLOBAL_REMOVED[@]}"; do
    echo "$item"
  done
else
  echo -e "\n未移除任何 implementation 依赖。"
fi

