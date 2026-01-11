#!/bin/sh

if [ -z "$1" ]; then
    echo "Usage: $(basename "$0") port"
    echo "Example: $(basename "$0") 8080"
    exit 1
fi

PORT=$1

# Try using lsof
if command -v lsof >/dev/null 2>&1; then
    PID=$(lsof -t -i :$PORT)
    if [ -n "$PID" ]; then
        echo "Killing process $PID on port $PORT..."
        kill -9 $PID
    else
        echo "Port $PORT is not in use."
    fi
# Try using fuser
elif command -v fuser >/dev/null 2>&1; then
    echo "Checking port $PORT using fuser..."
    fuser -k -n tcp $PORT
else
    echo "Error: neither lsof nor fuser found. Cannot kill port $PORT."
    exit 1
fi
