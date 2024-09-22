#export $(grep -v '^#' ./mini.env | xargs)
args=$(grep -v '^#' ./mini.env | grep -v '^$' | awk '{print "--build-arg " $0}' ORS=' ')
#docker build -f deploy/Dockerfile.koyeb -t local-koyeb .
echo $args
docker build $args \
  -f deploy/Dockerfile.koyeb \
  -t local-koyeb .
docker run \
  --privileged \
  -d \
  -v koyeb-docker:/var/lib/docker \
  local-koyeb
