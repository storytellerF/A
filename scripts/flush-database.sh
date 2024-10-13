set -e
sh gradlew cli:installDist
cli_path=cli/build/install/cli/bin/cli
base=$1
sh $cli_path clean

# Traverse and add all JSON files from the "$base/data" directory
for json_file in "$base/data"/*.json; do
  if [ -f "$json_file" ]; then
    sh $cli_path add "$json_file"
  fi
done
