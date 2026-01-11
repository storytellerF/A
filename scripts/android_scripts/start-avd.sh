#!/bin/bash

# Set the AVD name
AVD_NAME=$1

# Detect OS and set command suffix
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ "$OSTYPE" == "win32" ]] || [[ -n "$WINDIR" ]]; then
    CMD_SUFFIX=".exe"
    EMULATOR_SUFFIX=".bat"
else
    CMD_SUFFIX=""
    EMULATOR_SUFFIX=""
fi

# Set command names with appropriate suffix
ADB="adb"
EMULATOR="emulator"

# Function to check if the AVD is running
echo "Checking if AVD '$AVD_NAME' is running..."
if $ADB devices | grep -q "emulator"; then
    echo "AVD '$AVD_NAME' is running!"
    exit 0
else
    echo "AVD '$AVD_NAME' is not running."
fi

# Start the AVD if it's not running
echo "Starting AVD '$AVD_NAME'..."
export DISPLAY=:1
$EMULATOR -avd "$AVD_NAME" -no-snapshot-load &

# Wait for AVD to fully boot
counter=0
while [ $counter -lt 60 ]; do
    boot_completed=$($ADB shell getprop sys.boot_completed | tr -d '\r')
    if [ "$boot_completed" == "1" ]; then
        echo "AVD '$AVD_NAME' is fully booted!"
        exit 0
    fi
    echo "Waiting for AVD to finish booting..."
    sleep 5
    ((counter++))
done
echo "Timeout reached. AVD did not start."
exit 1
