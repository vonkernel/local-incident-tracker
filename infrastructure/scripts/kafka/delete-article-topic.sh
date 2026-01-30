#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

KAFKA_CONTAINER="lit-kafka"
BOOTSTRAP="localhost:9092"
TOPIC="lit.public.article"
CONSUMER_GROUP="analyzer-group"
CONNECTOR_NAME="lit-articles-connector"

echo "=== Delete: $TOPIC ==="
echo ""

# 1. Delete topic
echo "[1/3] Deleting topic: $TOPIC ..."
docker exec "$KAFKA_CONTAINER" kafka-topics \
  --bootstrap-server "$BOOTSTRAP" \
  --delete --topic "$TOPIC" 2>/dev/null
if [ $? -eq 0 ]; then
  echo "  Deleted topic: $TOPIC"
else
  echo "  Not found (skip): $TOPIC"
fi
echo ""

# 2. Delete consumer group
echo "[2/3] Deleting consumer group: $CONSUMER_GROUP ..."
docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
  --bootstrap-server "$BOOTSTRAP" \
  --delete --group "$CONSUMER_GROUP" 2>/dev/null
if [ $? -eq 0 ]; then
  echo "  Deleted group: $CONSUMER_GROUP"
else
  echo "  Not found (skip): $CONSUMER_GROUP"
fi
echo ""

# 3. Clear connector offset from debezium_connect_offsets
echo "[3/3] Clearing connector offset: $CONNECTOR_NAME ..."
echo "[\"$CONNECTOR_NAME\",{\"server\":\"lit\"}]=" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
  --bootstrap-server "$BOOTSTRAP" \
  --topic debezium_connect_offsets \
  --property parse.key=true \
  --property key.separator='=' \
  --property "null.marker=" 2>/dev/null
echo "  Cleared offset for: $CONNECTOR_NAME"
echo ""

echo "=== Done ==="
