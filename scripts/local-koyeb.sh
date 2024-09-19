. ./mini.env
docker build -f deploy/Dockerfile.koyeb -t local-koyeb .
docker run --privileged -d local-koyeb

