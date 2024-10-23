set -e
sh scripts/tool_scripts/patch-koyeb-env.sh
args=$(grep -v '^#' ./mini.env | grep -v '^$' | awk -F '=' '{print "--build-arg " $1 "=\"" $2 "\""}' ORS=' ')
eval docker build "$args" \
  -f deploy/Dockerfile.koyeb \
  -t local-koyeb .
docker run \
  --privileged \
  -d \
  -p 8811:8811 \
  -p 5432:5432 \
  -p 8812:8812 \
  local-koyeb
