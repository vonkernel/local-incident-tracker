#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

CONNECTOR_FILE="$SCRIPT_DIR/../../debezium/connectors/lit-analysis-connector.json"
DEBEZIUM_URL="http://localhost:${DEBEZIUM_PORT}"
CONNECTOR_NAME=$(jq -r '.name' "$CONNECTOR_FILE")

echo "=== Restart: $CONNECTOR_NAME ==="
echo ""

# Check Debezium Connect availability
if ! curl -f "$DEBEZIUM_URL/" > /dev/null 2>&1; then
  echo "Debezium Connect is not reachable at $DEBEZIUM_URL"
  exit 1
fi

# Restart connector
echo "Restarting connector: $CONNECTOR_NAME ..."
status_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/restart?includeTasks=true")
if [ "$status_code" = "204" ] || [ "$status_code" = "202" ]; then
  echo "  Restart triggered successfully"
elif [ "$status_code" = "404" ]; then
  echo "  Connector not found: $CONNECTOR_NAME"
  exit 1
else
  echo "  Failed (HTTP $status_code)"
  exit 1
fi
echo ""

sleep 3
echo "Connector status:"
curl -s "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/status" | jq '.'
echo ""
echo "Done!"
