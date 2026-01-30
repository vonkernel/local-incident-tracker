#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

KAFKA_CONTAINER="lit-kafka"
BOOTSTRAP="localhost:9092"

echo "=== Kafka Status ==="
echo ""

# 1. Topic list
echo "--- Topics ---"
docker exec "$KAFKA_CONTAINER" kafka-topics \
  --bootstrap-server "$BOOTSTRAP" --list
echo ""

# 2. Consumer group list
echo "--- Consumer Groups ---"
GROUPS=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
  --bootstrap-server "$BOOTSTRAP" --list 2>/dev/null)

if [ -z "$GROUPS" ]; then
  echo "  (no consumer groups)"
else
  echo "$GROUPS"
  echo ""

  # 3. Describe each group
  for group in $GROUPS; do
    echo "--- Group: $group ---"
    docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
      --bootstrap-server "$BOOTSTRAP" \
      --describe --group "$group"
    echo ""
  done
fi
