#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/.env"; set +a

CONNECTOR_FILE="$SCRIPT_DIR/debezium/connectors/lit-articles-connector.json"
DEBEZIUM_URL="http://localhost:${DEBEZIUM_PORT}"
CONNECTOR_NAME=$(jq -r '.name' "$CONNECTOR_FILE")
PUB_NAME=$(jq -r '.config["publication.name"]' "$CONNECTOR_FILE")
SLOT_NAME=$(jq -r '.config["slot.name"]' "$CONNECTOR_FILE")

echo "=== Reset: $CONNECTOR_NAME ==="
echo ""

# 1. Delete connector
echo "[1/4] Deleting connector..."
status_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME")
if [ "$status_code" = "204" ]; then
  echo "  Deleted: $CONNECTOR_NAME"
elif [ "$status_code" = "404" ]; then
  echo "  Not found (skip): $CONNECTOR_NAME"
else
  echo "  Failed (HTTP $status_code)"
fi
echo ""

# 2. Drop publication
echo "[2/4] Dropping publication: $PUB_NAME ..."
docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "DROP PUBLICATION IF EXISTS $PUB_NAME;" 2>/dev/null
echo ""

# 3. Drop replication slot
echo "[3/4] Dropping replication slot: $SLOT_NAME ..."
docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT pg_drop_replication_slot('$SLOT_NAME');" 2>/dev/null
echo ""

# 4. Clear Kafka offset for this connector
echo "[4/4] Clearing connector offset from Kafka..."
# Tombstone 메시지(key만 있고 value=null)를 전송하여 해당 커넥터의 offset을 삭제
echo "[\"$CONNECTOR_NAME\",{\"server\":\"lit\"}]=" | docker exec -i lit-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic debezium_connect_offsets \
  --property parse.key=true \
  --property key.separator='=' \
  --property "null.marker=" 2>/dev/null
echo "  Cleared offset for: $CONNECTOR_NAME"
echo ""

echo "=== Reset complete ==="
echo "Run ./setup-articles-connector.sh to re-register."
