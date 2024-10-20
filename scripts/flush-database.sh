set -e
sh gradlew cli:installDist
cli_path=cli/build/install/cli/bin/cli
base=$1
if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi
sh scripts/flush-database-singleton.sh $cli_path $base
