#!/bin/sh
set -e
# 检查参数个数
if [ "$#" -eq 2 ]; then
    echo "export parameter as env"
    export PRESET_ENCRYPTED_URI="$1"
    export PRESET_ENCRYPTED_PASSWORD="$2"
fi

if [ -z "$PRESET_ENCRYPTED_URI" ] || [ -z "$PRESET_ENCRYPTED_PASSWORD" ]; then
    echo "skip download preset data"
    exit 0
fi

# 临时文件名
TEMP_ZIP="preset.zip"

cd deploy

if [ -d preset_data ]; then
    echo "preset_data already exists, skip."
    exit 0
fi

# 下载 ZIP 文件
echo "Downloading file from $PRESET_ENCRYPTED_URI..."

# 检查文件是否已经存在
if [ -f "$TEMP_ZIP" ]; then
    echo "File '$TEMP_ZIP' already exists, skipping download."
else
    echo "File '$TEMP_ZIP' does not exist, downloading..."

    # 下载 ZIP 文件
    curl -L -o "$TEMP_ZIP" "$PRESET_ENCRYPTED_URI"

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

# 检查解压是否成功
if ! unzip -q -P "$PRESET_ENCRYPTED_PASSWORD" $TEMP_ZIP; then
    echo "Unzip failed! Check if the PRESET_ENCRYPTED_PASSWORD is correct."
    exit 1
fi

# 删除临时 ZIP 文件
#rm -f $TEMP_ZIP

echo "File downloaded and unzipped successfully!"

mv data preset_data
