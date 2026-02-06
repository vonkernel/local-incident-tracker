#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

KAFKA_CONTAINER="lit-kafka"
BOOTSTRAP="localhost:9092"
TOPIC="lit.analyzer.article-events.dlq"
CONSUMER_GROUP="analyzer-dlq-group"

echo "=== Delete: $TOPIC ==="
echo ""

# 1. Delete topic
echo "[1/2] Deleting topic: $TOPIC ..."
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
echo "[2/2] Deleting consumer group: $CONSUMER_GROUP ..."
docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
  --bootstrap-server "$BOOTSTRAP" \
  --delete --group "$CONSUMER_GROUP" 2>/dev/null
if [ $? -eq 0 ]; then
  echo "  Deleted group: $CONSUMER_GROUP"
else
  echo "  Not found (skip): $CONSUMER_GROUP"
fi
echo ""

echo "=== Done ==="
