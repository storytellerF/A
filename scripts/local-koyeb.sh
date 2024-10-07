set -e
sh scripts/patch-koyeb-env.sh
args=$(grep -v '^#' ./mini.env | grep -v '^$' | awk -F '=' '{print "--build-arg " $1 "=\"" $2 "\""}' ORS=' ')
eval docker build "$args" \
  -f deploy/Dockerfile.koyeb \
  -t local-koyeb .
docker run \
  --privileged \
  -d \
  -p 8811:8811 \
  local-koyeb
