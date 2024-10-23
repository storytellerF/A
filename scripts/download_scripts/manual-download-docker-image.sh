set -e
mkdir -p deploy/docker-images

# 要下载的文件
file="scripts/download-image.sh"
url="https://raw.githubusercontent.com/moby/moby/master/contrib/download-frozen-image-v2.sh"

# 判断文件是否已经存在
if [ -f "$file" ]; then
  echo "File '$file' already exists, skipping download."
else
  echo "File '$file' does not exist, downloading..."
  curl -o "$file" "$url"

  # 检查下载是否成功
  if [ $? -eq 0 ]; then
    echo "File '$file' downloaded successfully."
  else
    echo "Failed to download '$file'."
    exit 1
  fi
fi

# 函数：下载镜像并保存为 tar 文件
download_and_save() {
  IMAGE_NAME=$1        # Docker 镜像名称（例如：postgres, eclipse-temurin 等）
  IMAGE_TAG=$2         # Docker 镜像标签（例如：postgres:latest）
  OUTPUT_PATH=$3       # 保存 .tar 文件的路径

  # 检查文件是否存在
  if [ -f "$OUTPUT_PATH" ]; then
    echo "File $OUTPUT_PATH already exists, skipping download for $IMAGE_NAME."
  else
    echo "Downloading and saving image $IMAGE_NAME:$IMAGE_TAG..."

    # 下载镜像到指定文件夹
    bash $file "$IMAGE_NAME" "$IMAGE_TAG"

    # 打包镜像到 .tar 文件
    tar -C "$IMAGE_NAME" -cf "$OUTPUT_PATH" .

    echo "Image $IMAGE_NAME:$IMAGE_TAG saved to $OUTPUT_PATH."
  fi
}

# 调用函数下载和保存各个镜像
download_and_save "eclipse-temurin" "eclipse-temurin:21" "deploy/docker-images/eclipse-temurin.tar"
download_and_save "postgres" "postgres:latest" "deploy/docker-images/postgres.tar"
download_and_save "adminer" "adminer:latest" "deploy/docker-images/adminer.tar"
download_and_save "minio" "minio/minio:latest" "deploy/docker-images/minio.tar"
