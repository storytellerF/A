#!/bin/sh
find scripts/ -type f \( -name "*.sh" -o -name "*.js" \) -exec chmod +x {} + 