docker run \
  --name kibana \
  --net elastic \
  -p 5601:5601 \
  -v ./elastic_root/kibana_config:/usr/share/kibana/config
  docker.elastic.co/kibana/kibana:8.14.3