#!/bin/sh
set -eu
base=/home/app/logs/e2e-coverage
found=false
for directory in "$base"/*; do
    [ -d "$directory" ] || continue
    pid=${directory##*/}
    case "$pid" in ''|*[!0-9]*) exit 2 ;; esac
    [ -f "$directory/ready" ] || exit 3
    [ ! -f "$directory/error" ] || exit 4
    found=true
    if [ -f "$directory/running" ]; then
        rm -f "$directory/complete"
        printf snapshot > "$directory/request.pending"
        mv "$directory/request.pending" "$directory/request"
    fi
    polls=0
    while [ ! -f "$directory/complete" ]; do
        [ ! -f "$directory/error" ] || exit 4
        [ "$polls" -lt 300 ] || exit 5
        polls=$((polls + 1))
        sleep 0.05
    done
    [ ! -f "$directory/error" ] || exit 4
    [ -s "$directory/report.ic" ] || exit 6
    printf '%s\n' "$pid"
done
[ "$found" = true ] || exit 7
