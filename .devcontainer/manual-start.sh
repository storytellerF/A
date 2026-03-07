#!/bin/sh
if docker compose -f ./docker-compose.dev.yml up -d --build; then
    echo "Docker compose started successfully."
    echo "You can access the Android emulator via:"
    echo "  - Web VNC: http://localhost:6080/vnc.html"
    echo "  - VNC direct: localhost:5901"
    echo "  - Appium: http://localhost:4723/inspector"
else
    echo "Failed to start docker compose."
fi