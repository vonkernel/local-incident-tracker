#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/.env"; set +a

CONNECTORS_DIR="$SCRIPT_DIR/debezium/connectors"
DEBEZIUM_URL="http://localhost:${DEBEZIUM_PORT}"

echo "=== Debezium Connector Status ==="
echo ""

# Check Debezium Connect availability
if ! curl -f "$DEBEZIUM_URL/" > /dev/null 2>&1; then
  echo "Debezium Connect is not reachable at $DEBEZIUM_URL"
  exit 1
fi

# Connector statuses
for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  echo "--- $connector_name ---"
  curl -s "$DEBEZIUM_URL/connectors/$connector_name/status" | jq '.'
  echo ""
done

# PostgreSQL replication slots
echo "--- Replication Slots ---"
docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT slot_name, plugin, slot_type, active FROM pg_replication_slots;" 2>/dev/null
echo ""

# PostgreSQL publications
echo "--- Publications ---"
docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT pubname, puballtables FROM pg_publication;" 2>/dev/null
