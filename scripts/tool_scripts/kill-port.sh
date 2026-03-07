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
# Try using netstat
elif command -v netstat >/dev/null 2>&1; then
    echo "Checking port $PORT using netstat..."
    PID=$(netstat -tulpn 2>/dev/null | grep ":$PORT " | head -1 | awk '{print $7}' | cut -d'/' -f1)
    if [ -n "$PID" ] && [ "$PID" != "-" ]; then
        echo "Killing process $PID on port $PORT..."
        kill -9 $PID
    else
        echo "Port $PORT is not in use or no PID found."
    fi
# Try using ss
elif command -v ss >/dev/null 2>&1; then
    echo "Checking port $PORT using ss..."
    PID=$(ss -tulpn 2>/dev/null | grep ":$PORT " | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)
    if [ -n "$PID" ]; then
        echo "Killing process $PID on port $PORT..."
        kill -9 $PID
    else
        echo "Port $PORT is not in use or no PID found."
    fi
else
    echo "Error: neither lsof, fuser, netstat, nor ss found. Cannot kill port $PORT."
    exit 1
fi
