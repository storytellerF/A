#!/bin/bash
set -e
echo $#
# 检查参数个数
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <download_url> <password>"
    exit 1
fi

# 获取参数
DOWNLOAD_URL=$1
PASSWORD=$2

# 临时文件名
TEMP_ZIP="downloaded_file.zip"

# 下载 ZIP 文件
echo "Downloading file from $DOWNLOAD_URL..."
curl -L -o $TEMP_ZIP $DOWNLOAD_URL

# 检查是否下载成功
if [ $? -ne 0 ]; then
    echo "Download failed!"
    exit 1
fi

# 解压缩文件
echo "Unzipping file..."
unzip -h
unzip -q -P $PASSWORD $TEMP_ZIP

# 检查解压是否成功
if [ $? -ne 0 ]; then
    echo "Unzip failed! Check if the password is correct."
    exit 1
fi

# 删除临时 ZIP 文件
rm -f $TEMP_ZIP

echo "File downloaded and unzipped successfully!"
