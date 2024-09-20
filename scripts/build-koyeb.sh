if [ -f "./mini.env" ]; then
    export $(grep -v '^#' ./mini.env | xargs)
else
    echo "File does not exist, skipping."
fi

docker compose up