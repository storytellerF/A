set -e
sh gradlew cli:installDist
base=$1
sh cli/build/install/cli/bin/cli clean
sh cli/build/install/cli/bin/cli add $base/data/preset_user.json
sh cli/build/install/cli/bin/cli add $base/data/preset_community.json
sh cli/build/install/cli/bin/cli add $base/data/preset_room.json
sh cli/build/install/cli/bin/cli add $base/data/preset_topic.json
