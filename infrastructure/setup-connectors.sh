#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONNECTORS_DIR="$SCRIPT_DIR/debezium/connectors"
DEBEZIUM_URL="http://localhost:18083"

# Wait for Debezium Connect to be ready
echo "Waiting for Debezium Connect to be ready..."
until curl -f "$DEBEZIUM_URL/" > /dev/null 2>&1; do
  echo "Debezium Connect not ready yet. Retrying in 5 seconds..."
  sleep 5
done

echo "Debezium Connect is ready!"
echo ""

# Register all connector JSON files
for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  echo "Registering connector: $connector_name ($connector_file)..."
  curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
    "$DEBEZIUM_URL/connectors/" -d @"$connector_file"
  echo ""
  echo ""
done

# Wait for tasks to be assigned
sleep 5

# Check all connector statuses
echo "Checking connector statuses..."
for connector_file in "$CONNECTORS_DIR"/*.json; do
  connector_name=$(jq -r '.name' "$connector_file")
  echo "--- $connector_name ---"
  curl -s "$DEBEZIUM_URL/connectors/$connector_name/status" | jq '.'
  echo ""
done

echo "Done!"