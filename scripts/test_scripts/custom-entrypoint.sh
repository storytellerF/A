#!/bin/bash
# Custom entrypoint script for executing various maintenance tasks
# Task execution status is tracked in ~/.a/cmd

set -e

# State file path
STATE_FILE="$HOME/.a/cmd"

# Ensure state directory exists
mkdir -p "$(dirname "$STATE_FILE")"
touch "$STATE_FILE"

# Check if a task has been completed
is_completed() {
    local task="$1"
    grep -q "^${task}$" "$STATE_FILE" 2>/dev/null
}

# Mark a task as completed
mark_completed() {
    local task="$1"
    if ! is_completed "$task"; then
        echo "$task" >> "$STATE_FILE"
    fi
}

# Fix Volume Permissions for commandhistory
fix_volume() {
    local task="fix-volume"
    if is_completed "$task"; then
        echo "[$task] Already completed, skipping..."
        return 0
    fi
    echo "Fixing Volume Permissions..."
    sudo chown -R $(whoami): /commandhistory
    mark_completed "$task"
    echo "[$task] Completed"
}

# Fix Gradle Volume Permissions
fix_gradle() {
    local task="fix-gradle"
    if is_completed "$task"; then
        echo "[$task] Already completed, skipping..."
        return 0
    fi
    echo "Fixing Gradle Volume Permissions..."
    sudo chown -R $(whoami): /home/ubuntu/.gradle
    mark_completed "$task"
    echo "[$task] Completed"
}

# Fix Android Volume Permissions
fix_android() {
    local task="fix-android"
    if is_completed "$task"; then
        echo "[$task] Already completed, skipping..."
        return 0
    fi
    echo "Fixing Android Volume Permissions..."
    sudo chown -R $(whoami): /home/ubuntu/.android
    mark_completed "$task"
    echo "[$task] Completed"
}

# Fix Konan Volume Permissions
fix_konan() {
    local task="fix-konan"
    if is_completed "$task"; then
        echo "[$task] Already completed, skipping..."
        return 0
    fi
    echo "Fixing Konan Volume Permissions..."
    sudo chown -R $(whoami): /home/ubuntu/.konan
    mark_completed "$task"
    echo "[$task] Completed"
}

# Fix M2 Volume Permissions
fix_m2() {
    local task="fix-m2"
    if is_completed "$task"; then
        echo "[$task] Already completed, skipping..."
        return 0
    fi
    echo "Fixing M2 Volume Permissions..."
    sudo chown -R $(whoami): /home/ubuntu/.m2
    mark_completed "$task"
    echo "[$task] Completed"
}

# Setup Git LFS
setup_git_lfs() {
    local task="setup-git-lfs"
    if is_completed "$task"; then
        echo "[$task] Already completed, skipping..."
        return 0
    fi
    echo "Setting up Git LFS..."
    sudo ./scripts/tool_scripts/install-lfs.sh
    mark_completed "$task"
    echo "[$task] Completed"
}

# Show completion status of all tasks
show_status() {
    echo "Task completion status:"
    echo ""

    local tasks=("fix-volume" "fix-gradle" "fix-android" "fix-konan" "fix-m2" "setup-git-lfs")

    for task in "${tasks[@]}"; do
        if is_completed "$task"; then
            echo "  [$task] ✓ Completed"
        else
            echo "  [$task] ✗ Not completed"
        fi
    done
    echo ""
    echo "State file: $STATE_FILE"
}

# Execute all tasks
all_tasks() {
    echo "Executing all pending tasks..."
    fix_volume
    fix_gradle
    fix_android
    fix_konan
    fix_m2
    setup_git_lfs
    echo ""
    show_status
}

# Main execution
all_tasks

./bin/entrypoint.sh