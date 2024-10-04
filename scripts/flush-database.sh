set -e
sh gradlew cli:installDist
cli_path=cli/build/install/cli/bin/cli
base=$1
sh $cli_path clean
if [ -f "$base/data/pre_set_user.json" ]; then
  sh $cli_path add $base/data/pre_set_user.json
fi
if [ -f "$base/data/pre_set_community.json" ]; then
  sh $cli_path add $base/data/pre_set_community.json
fi
if [ -f "$base/data/pre_set_room.json" ]; then
  sh $cli_path add $base/data/pre_set_room.json
fi
if [ -f "$base/data/pre_set_topic.json" ]; then
  sh $cli_path add $base/data/pre_set_topic.json
fi
