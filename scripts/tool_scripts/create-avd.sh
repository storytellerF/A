#!/bin/bash

# Set emulator name and device parameters
AVD_NAME="ATest"
SYSTEM_IMAGE="system-images;android-30;default;x86_64"
DEVICE="pixel_4"

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check if required tools are installed
if ! command_exists sdkmanager || ! command_exists avdmanager || ! command_exists emulator; then
    echo "Error: Android SDK tools (sdkmanager, avdmanager, emulator) are not available in PATH."
    echo "Please ensure Android SDK is installed and environment variables are set correctly."
    exit 1
fi

# Check if AVD already exists
echo "Checking if AVD '$AVD_NAME' exists..."
if avdmanager list avd | grep -q "$AVD_NAME"; then
    echo "AVD '$AVD_NAME' already exists. Skipping creation."
    exit 0
fi

echo "AVD '$AVD_NAME' does not exist. Checking system image..."

# Check if the system image is installed
if ! sdkmanager --list | grep -q "$SYSTEM_IMAGE"; then
    echo "System image '$SYSTEM_IMAGE' is not installed. Downloading..."
    sdkmanager "$SYSTEM_IMAGE"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to download system image. Please check your SDK Manager."
        exit 1
    fi
    echo "System image downloaded successfully."
else
    echo "System image is already installed. Skipping download."
fi

# Create the new AVD
echo "Creating AVD '$AVD_NAME'..."
echo no | avdmanager create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "$DEVICE"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create AVD. Please check your SDK configuration."
    exit 1
fi
echo "AVD '$AVD_NAME' created successfully!"

# Ask the user whether to start the emulator
read -p "Do you want to start the emulator now? (Y/N): " START_AVD
if [[ "$START_AVD" =~ ^[Yy]$ ]]; then
    echo "Starting AVD '$AVD_NAME'..."
    emulator -avd "$AVD_NAME" &
else
    echo "You can start it later using: emulator -avd $AVD_NAME"
fi

exit 0
