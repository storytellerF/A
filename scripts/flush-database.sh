set -e
sh gradlew cli:installDist
cli_path=cli/build/install/cli/bin/cli
base=$1
sh flush-database-singleton.sh $cli_path $base
