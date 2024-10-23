set -e
cli_path=$1
base=$2
sh $cli_path clean

index=0

for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    filename=$(basename -- "$json_file")

    file_index=$(echo "$filename" | grep -o '^[0-9]\+')

    if [ "$file_index" -eq "$index" ]; then
      sh "$cli_path" add "$json_file"
    fi

    index=$((index + 1))
  fi
done