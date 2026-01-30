#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

CONNECTOR_FILE="$SCRIPT_DIR/../../debezium/connectors/lit-articles-connector.json"
DEBEZIUM_URL="http://localhost:${DEBEZIUM_PORT}"
CONNECTOR_NAME=$(jq -r '.name' "$CONNECTOR_FILE")

echo "=== Setup: $CONNECTOR_NAME ==="
echo ""

echo "Waiting for Debezium Connect to be ready..."
until curl -f "$DEBEZIUM_URL/" > /dev/null 2>&1; do
  echo "  Retrying in 5 seconds..."
  sleep 5
done
echo "Debezium Connect is ready!"
echo ""

echo "Registering connector: $CONNECTOR_NAME ..."
envsubst < "$CONNECTOR_FILE" | curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  "$DEBEZIUM_URL/connectors/" -d @-
echo ""
echo ""

sleep 5
echo "Connector status:"
curl -s "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/status" | jq '.'
echo ""
echo "Done!"
