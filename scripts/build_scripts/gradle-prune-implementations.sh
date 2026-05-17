#!/usr/bin/env bash
# 自动检测并移除所有模块 build.gradle.kts 中未被使用的 implementation 依赖
# 用法：在项目根目录下运行本脚本。额外参数会原样传给 Gradle。

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
GRADLEW="$ROOT_DIR/gradlew"
CHECK_TASK="${GRADLE_PRUNE_TASK:-assemble}"
GRADLE_ARGS=("$CHECK_TASK" "--console=plain" "$@")

ACTIVE_FILE=""
ACTIVE_BACKUP=""

restore_active_file() {
  if [[ -n "$ACTIVE_FILE" && -n "$ACTIVE_BACKUP" && -f "$ACTIVE_BACKUP" ]]; then
    cp "$ACTIVE_BACKUP" "$ACTIVE_FILE"
  fi
}

cleanup_on_exit() {
  restore_active_file
}

trap cleanup_on_exit EXIT
trap 'cleanup_on_exit; exit 130' INT TERM

extract_implementation_ranges() {
  local file=$1
  awk '
    function brace_delta(text, i, ch, delta) {
      delta = 0
      for (i = 1; i <= length(text); i++) {
        ch = substr(text, i, 1)
        if (ch == "{") {
          delta++
        } else if (ch == "}") {
          delta--
        }
      }
      return delta
    }

    BEGIN {
      depth = 0
      dependencies_depth = 0
      implementation_start = 0
      implementation_depth = 0
      implementation_text = ""
    }

    {
      line = $0
      delta = brace_delta(line)
      next_depth = depth + delta

      if (dependencies_depth == 0 && line ~ /(^|[[:space:].])dependencies[[:space:]]*[{]/) {
        dependencies_depth = depth + 1
      }

      if (dependencies_depth > 0 && implementation_start == 0 && line ~ /^[[:space:]]*implementation[[:space:]]*[(]/) {
        implementation_start = NR
        implementation_text = line
        if (line !~ /[{]/ || next_depth <= depth) {
          printf "%d\t%d\t%s\n", implementation_start, NR, implementation_text
          implementation_start = 0
          implementation_text = ""
        } else {
          implementation_depth = next_depth
        }
      } else if (implementation_start > 0 && next_depth < implementation_depth) {
        printf "%d\t%d\t%s\n", implementation_start, NR, implementation_text
        implementation_start = 0
        implementation_depth = 0
        implementation_text = ""
      }

      if (dependencies_depth > 0 && next_depth < dependencies_depth) {
        dependencies_depth = 0
      }

      depth = next_depth
    }
  ' "$file"
}

comment_range() {
  local file=$1
  local start=$2
  local end=$3
  sed -i "${start},${end}s#^#//#" "$file"
}

delete_range() {
  local file=$1
  local start=$2
  local end=$3
  sed -i "${start},${end}d" "$file"
}

cd "$ROOT_DIR"

echo "读取 Gradle 项目列表 ..."
if ! PROJECTS_OUTPUT=$("$GRADLEW" -q projects --console=plain); then
  echo "无法读取 Gradle 项目列表，停止。"
  exit 1
fi

mapfile -t PROJECT_PATHS < <(printf '%s\n' "$PROJECTS_OUTPUT" \
  | sed -n "s/.*Project '\(:[^']*\)'.*/\1/p" \
  | sort -u)

echo "查找当前构建包含的模块 build.gradle.kts ..."
declare -a GRADLE_FILES
declare -a PROJECTS
for PROJECT_PATH in "${PROJECT_PATHS[@]}"; do
  PROJECT_DIR=${PROJECT_PATH#:}
  PROJECT_DIR=${PROJECT_DIR//:/\/}
  GRADLE_FILE="$ROOT_DIR/$PROJECT_DIR/build.gradle.kts"
  if [[ -f "$GRADLE_FILE" ]]; then
    GRADLE_FILES+=("$GRADLE_FILE")
    PROJECTS+=("$PROJECT_PATH")
  fi
done

if [ ${#GRADLE_FILES[@]} -eq 0 ]; then
  echo "未找到任何模块的 build.gradle.kts 文件。"
  exit 0
fi


# 全局汇总数组
declare -a GLOBAL_REMOVED

for idx in "${!GRADLE_FILES[@]}"; do
  GRADLE_FILE=${GRADLE_FILES[$idx]}
  PROJECT_PATH=${PROJECTS[$idx]}
  MODULE_DIR=$(dirname "$GRADLE_FILE")
  echo -e "\n=============================="
  echo "模块: $PROJECT_PATH ($MODULE_DIR)"
  BACKUP_FILE="$GRADLE_FILE.bak.prune"
  TMP_FILE="$GRADLE_FILE.tmp.prune"
  ATTEMPT_FILE="$GRADLE_FILE.attempt.prune"
  LOG_FILE="$GRADLE_FILE.log.prune"

  ACTIVE_FILE="$GRADLE_FILE"
  ACTIVE_BACKUP="$BACKUP_FILE"

  cp "$GRADLE_FILE" "$BACKUP_FILE"
  echo "备份 build.gradle.kts 到 $BACKUP_FILE"

  mapfile -t lines < <(extract_implementation_ranges "$GRADLE_FILE")
  if [ ${#lines[@]} -eq 0 ]; then
    echo "未找到 implementation 依赖，跳过。"
    rm -f "$BACKUP_FILE" "$TMP_FILE" "$ATTEMPT_FILE" "$LOG_FILE"
    ACTIVE_FILE=""
    ACTIVE_BACKUP=""
    continue
  fi

  REMOVABLE=()
  removable_starts=()
  removable_ends=()
  for line_idx in "${!lines[@]}"; do
    entry="${lines[$line_idx]}"
    IFS=$'\t' read -r start_line end_line dep <<< "$entry"
    if [[ "$start_line" == "$end_line" ]]; then
      echo -e "\n尝试移除: $dep (第 $start_line 行)"
    else
      echo -e "\n尝试移除: $dep (第 $start_line-$end_line 行)"
    fi

    cp "$GRADLE_FILE" "$ATTEMPT_FILE"
    cp "$GRADLE_FILE" "$TMP_FILE"
    comment_range "$TMP_FILE" "$start_line" "$end_line"
    mv "$TMP_FILE" "$GRADLE_FILE"

    if "$GRADLEW" "${GRADLE_ARGS[@]}" > "$LOG_FILE" 2>&1; then
      echo "可以移除: $dep"
      REMOVABLE+=("$dep")
      removable_starts+=("$start_line")
      removable_ends+=("$end_line")
    else
      echo "不能移除: $dep，恢复..."
      cp "$ATTEMPT_FILE" "$GRADLE_FILE"
    fi
  done

  if [ ${#removable_starts[@]} -gt 0 ]; then
    for ((i=${#removable_starts[@]}-1; i>=0; i--)); do
      delete_range "$GRADLE_FILE" "${removable_starts[$i]}" "${removable_ends[$i]}"
    done
    echo -e "\n已自动移除以下 implementation 依赖："
    for dep in "${REMOVABLE[@]}"; do
      echo "$dep"
      GLOBAL_REMOVED+=("$MODULE_DIR: $dep")
    done
  else
    cp "$BACKUP_FILE" "$GRADLE_FILE"
    echo "没有可安全移除的 implementation 依赖。"
  fi

  rm -f "$BACKUP_FILE" "$TMP_FILE" "$ATTEMPT_FILE" "$LOG_FILE"
  ACTIVE_FILE=""
  ACTIVE_BACKUP=""
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
