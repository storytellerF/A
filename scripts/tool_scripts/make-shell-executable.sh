#!/bin/sh
SCRIPTS_DIR="scripts/"

check_scripts() {
    has_errors=0
    
    while IFS= read -r file; do
        if ! head -n 1 "$file" | grep -q "^#!"; then
            echo "Error: Missing shebang in $file" >&2
            has_errors=1
        fi
    done
    
    return $has_errors
}

# 使用管道将find结果传递给while循环
if ! find "$SCRIPTS_DIR" -type f -name "*.sh" -print | check_scripts; then
    echo "Error: Some scripts are missing shebang lines" >&2
    exit 1
fi

find "$SCRIPTS_DIR" -type f -name "*.sh" -exec chmod +x {} +
