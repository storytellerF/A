#!/bin/bash

# Set the AVD name
AVD_NAME="ATest"

# Function to check if the AVD is running
check_avd() {
    echo "Checking if AVD '$AVD_NAME' is running..."
    adb devices | grep -q "emulator"
    if [ $? -eq 0 ]; then
        echo "AVD '$AVD_NAME' is running!"
        exit 0
    else
        echo "AVD '$AVD_NAME' is not running."
    fi
}

# Start the AVD if it's not running
start_avd() {
    echo "Starting AVD '$AVD_NAME'..."
    emulator -avd "$AVD_NAME" -no-snapshot-load &
}

# Wait for AVD to fully boot
wait_for_boot() {
    local counter=0
    while [ $counter -lt 60 ]; do
        boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')
        if [ "$boot_completed" == "1" ]; then
            echo "AVD '$AVD_NAME' is fully booted!"
            exit 0
        else
            echo "Waiting for AVD to finish booting..."
            sleep 5
            ((counter++))
        fi
    done
    echo "Timeout reached. AVD did not start."
    exit 1
}

# Check if AVD is running, otherwise start and wait for it to boot
check_avd
start_avd
wait_for_boot
