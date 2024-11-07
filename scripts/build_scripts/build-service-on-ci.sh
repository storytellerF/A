#!/bin/bash

set -e

TEMP_FILE=./temp

# Pipe the JSON string into jq
echo "$VARS_CONTEXT" | 
# Convert JSON object into an array of key-value pairs
jq -r 'to_entries | 
# Map over each key-value pair
.[] | 
# Format each pair as "KEY=VALUE" and append it all to the environment file
"\(.key)=\(.value)"' >> $TEMP_FILE

# Pipe the JSON string into jq
echo "$SECRETS_CONTEXT" | 
# Convert JSON object into an array of key-value pairs
jq -r 'to_entries | 
# Map over each key-value pair
.[] | 
# Format each pair as "KEY=VALUE" and append it all to the environment file
"\(.key)=\(.value)"' >> $TEMP_FILE

while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    export "$key"="$value"
done < $TEMP_FILE

./scripts/tool_scripts/save-env.sh

./scripts/tool_scripts/modify-flavor.sh "s-$FLAVOR" true

./gradlew composeApp:build

mkdir -p "build/outputs/apk/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/s-$FLAVOR.apk"
