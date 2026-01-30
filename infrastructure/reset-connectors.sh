#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/.env"; set +a

CONNECTORS_DIR="$SCRIPT_DIR/debezium/connectors"
DEBEZIUM_URL="http://localhost:${DEBEZIUM_PORT}"

echo "=== Debezium Full Reset ==="
echo ""

# 1. Delete connectors
echo "[1/5] Deleting connectors..."
for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  status_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$DEBEZIUM_URL/connectors/$connector_name")
  if [ "$status_code" = "204" ]; then
    echo "  Deleted: $connector_name"
  elif [ "$status_code" = "404" ]; then
    echo "  Not found (skip): $connector_name"
  else
    echo "  Failed to delete $connector_name (HTTP $status_code)"
  fi
done
echo ""

# 2. Drop publications
echo "[2/5] Dropping publications..."
for connector_file in "$CONNECTORS_DIR"/*.json; do
  pub_name=$(jq -r '.config["publication.name"]' "$connector_file")
  docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "DROP PUBLICATION IF EXISTS $pub_name;" 2>/dev/null
  echo "  Dropped: $pub_name"
done
echo ""

# 3. Drop replication slots
echo "[3/5] Dropping replication slots..."
for connector_file in "$CONNECTORS_DIR"/*.json; do
  slot_name=$(jq -r '.config["slot.name"]' "$connector_file")
  docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "SELECT pg_drop_replication_slot('$slot_name');" 2>/dev/null
  if [ $? -eq 0 ]; then
    echo "  Dropped: $slot_name"
  else
    echo "  Not found (skip): $slot_name"
  fi
done
echo ""

# 4. Clear Kafka offsets for all connectors
echo "[4/5] Clearing connector offsets from Kafka..."
for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  echo "[\"$connector_name\",{\"server\":\"lit\"}]=" | docker exec -i lit-kafka kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic debezium_connect_offsets \
    --property parse.key=true \
    --property key.separator='=' \
    --property "null.marker=" 2>/dev/null
  echo "  Cleared offset for: $connector_name"
done
echo ""

# 5. Restart Debezium
echo "[5/5] Restarting Debezium Connect..."
docker restart lit-debezium > /dev/null 2>&1
echo "  Waiting for Debezium Connect to be ready..."
until curl -f "$DEBEZIUM_URL/" > /dev/null 2>&1; do
  sleep 3
done
echo "  Debezium Connect is ready!"
echo ""

echo "=== Reset complete ==="
echo "Run setup scripts to re-register connectors:"
echo "  ./setup-articles-connector.sh"
echo "  ./setup-analysis-connector.sh"
