set -e
PUSH_TO_REMOTE_URI=$1
REMOTE_CERT_FILE=$2
REMOTE_COMMANd=$3
if [ -z "$PUSH_TO_REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ]; then
  echo "PUSH_TO_REMOTE_URI and REMOTE_CERT_FILE must be set"
  exet 1
fi
ssh -i $REMOTE_CERT_FILE -p 422 $PUSH_TO_REMOTE_URI "mkdir -p a-server"

echo "put $FILE ./a-server/image.tar" | sftp -i $REMOTE_CERT_FILE $PUSH_TO_REMOTE_URI

ssh -i $REMOTE_CERT_FILE -p 422 $PUSH_TO_REMOTE_URI $REMOTE_COMMANd