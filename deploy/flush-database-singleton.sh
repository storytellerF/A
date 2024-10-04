set -e
cli_path=$1
base=$2
sh $cli_path clean
if [ -f "$base/pre_set_user.json" ]; then
  sh $cli_path add $base/pre_set_user.json
fi
if [ -f "$base/pre_set_community.json" ]; then
  sh $cli_path add $base/pre_set_community.json
fi
if [ -f "$base/pre_set_room.json" ]; then
  sh $cli_path add $base/pre_set_room.json
fi
if [ -f "$base/pre_set_topic.json" ]; then
  sh $cli_path add $base/pre_set_topic.json
fi
