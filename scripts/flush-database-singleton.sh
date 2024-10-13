set -e
cli_path=$1
base=$2
sh $cli_path clean

# Traverse and add all JSON files from the directory
for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    sh $cli_path add "$json_file"
  fi
done
