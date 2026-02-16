#!/bin/sh
docker compose -f ./docker-compose.dev.yml up -d --build

echo "VNC URL: http://localhost:6081/vnc.html"
echo "Appium URL: http://localhost:4724/inspector"