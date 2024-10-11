#!/bin/bash
set -e
# 检查参数个数
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <download_url> <password>"
    exit 1
fi

# 获取参数
DOWNLOAD_URL=$1
PASSWORD=$2

# 临时文件名
TEMP_ZIP="pre_set.zip"

cd deploy

if [ -d pre_set_data ]; then
    echo "pre_set_data already exists, skip."
    exit 0
fi

# 下载 ZIP 文件
echo "Downloading file from $DOWNLOAD_URL..."

# 检查文件是否已经存在
if [ -f "$TEMP_ZIP" ]; then
    echo "File '$TEMP_ZIP' already exists, skipping download."
else
    echo "File '$TEMP_ZIP' does not exist, downloading..."

    # 下载 ZIP 文件
    curl -L -o "$TEMP_ZIP" "$DOWNLOAD_URL"

    # 检查下载是否成功
    if [ $? -ne 0 ]; then
        echo "Download failed!"
        exit 1
    else
        echo "File '$TEMP_ZIP' downloaded successfully."
    fi
fi

# 解压缩文件
echo "Unzipping file..."
unzip -q -P $PASSWORD $TEMP_ZIP

# 检查解压是否成功
if [ $? -ne 0 ]; then
    echo "Unzip failed! Check if the password is correct."
    exit 1
fi

# 删除临时 ZIP 文件
#rm -f $TEMP_ZIP

echo "File downloaded and unzipped successfully!"

mv data pre_set_data
