set -e
mkdir -p deploy/docker-images
curl -o download-image.sh https://raw.githubusercontent.com/moby/moby/master/contrib/download-frozen-image-v2.sh

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
    bash download-image.sh "$IMAGE_NAME" "$IMAGE_TAG"

    # 打包镜像到 .tar 文件
    tar -C "$IMAGE_NAME" -cf "$OUTPUT_PATH" .

    echo "Image $IMAGE_NAME:$IMAGE_TAG saved to $OUTPUT_PATH."
  fi
}

# 调用函数下载和保存各个镜像
download_and_save "eclipse-temurin" "eclipse-temurin:17" "deploy/docker-images/eclipse-temurin.tar"
download_and_save "postgres" "postgres:latest" "deploy/docker-images/postgres.tar"
download_and_save "adminer" "adminer:latest" "deploy/docker-images/adminer.tar"
download_and_save "minio" "minio/minio:latest" "deploy/docker-images/minio.tar"
