#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Debezium Full Delete ==="
echo ""

"$SCRIPT_DIR/delete-articles-connector.sh"
"$SCRIPT_DIR/delete-analysis-connector.sh"

echo "=== Delete complete ==="
