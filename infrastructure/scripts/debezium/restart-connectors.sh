#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Debezium Full Restart ==="
echo ""

"$SCRIPT_DIR/restart-articles-connector.sh"
"$SCRIPT_DIR/restart-analysis-connector.sh"

echo "=== Restart complete ==="
