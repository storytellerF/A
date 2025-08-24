#!/bin/bash

# Set the AVD name
AVD_NAME=$1

# Function to check if the AVD is running
echo "Checking if AVD '$AVD_NAME' is running..."
if adb devices | grep -q "emulator"; then
    echo "AVD '$AVD_NAME' is running!"
    exit 0
else
    echo "AVD '$AVD_NAME' is not running."
fi

# Start the AVD if it's not running
echo "Starting AVD '$AVD_NAME'..."
emulator -avd "$AVD_NAME" -no-snapshot-load &

# Wait for AVD to fully boot
counter=0
while [ $counter -lt 60 ]; do
    boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')
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
