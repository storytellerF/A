docker run \
    --name elasticsearch \
    --net elastic \
    -v ./elastic_root/config:/usr/share/elasticsearch/config \
    -p 9200:9200 \
    -p 9300:9300 \
    -e "discovery.type=single-node" \
    -t \
    docker.elastic.co/elasticsearch/elasticsearch:8.14.3
