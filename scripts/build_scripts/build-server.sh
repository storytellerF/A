set -e
sh scripts/tool_scripts/check-all-env.sh
sh gradlew server:buildFatJar --no-daemon
cp ./server/build/libs/*-all.jar ./deploy/build
