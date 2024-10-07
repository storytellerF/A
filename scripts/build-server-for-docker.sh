[ -e deploy/build ] || mkdir deploy/build
sh scripts/build-server.sh
cp ./server/build/libs/*-all.jar deploy/build
sh scripts/build-cli.sh
cp ./cli/build/distributions/cli.* deploy/build