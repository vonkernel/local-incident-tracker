#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/.env"; set +a

CONNECTORS_DIR="$SCRIPT_DIR/debezium/connectors"
DEBEZIUM_URL="http://localhost:${DEBEZIUM_PORT}"

echo "Waiting for Debezium Connect to be ready..."
until curl -f "$DEBEZIUM_URL/" > /dev/null 2>&1; do
  echo "  Retrying in 5 seconds..."
  sleep 5
done
echo "Debezium Connect is ready!"
echo ""

for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  echo "Registering connector: $connector_name ..."
  envsubst < "$connector_file" | curl -s -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
    "$DEBEZIUM_URL/connectors/" -d @-
  echo ""
  echo ""
done

sleep 5

echo "Checking connector statuses..."
for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  echo "--- $connector_name ---"
  curl -s "$DEBEZIUM_URL/connectors/$connector_name/status" | jq '.'
  echo ""
done

echo "Done!"
