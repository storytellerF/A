set -e
cli_path=$1
base=$2

if [ -z "$base" ] || [ -z "$cli_path" ]; then
  echo "Error: base and cli_path parameter is not specified."
  exit 1
fi

sh $cli_path clean

echo "clean result is $?"

index=0

for json_file in "$base"/*.json; do
  if [ -f "$json_file" ]; then
    filename=$(basename -- "$json_file")

    file_index=$(echo "$filename" | grep -o '^[0-9]\+')

    if [ "$file_index" -eq "$index" ]; then
      echo "Process $json_file"
      sh "$cli_path" add "$json_file"
      echo "result is $?"
    fi

    index=$((index + 1))
  fi
done