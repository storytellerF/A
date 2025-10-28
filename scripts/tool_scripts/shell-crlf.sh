#!/bin/sh
find . -type f -name "*.sh" -exec dos2unix {} + && dos2unix gradlew