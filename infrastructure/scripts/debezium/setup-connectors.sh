#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Debezium Full Setup ==="
echo ""

"$SCRIPT_DIR/setup-articles-connector.sh"
"$SCRIPT_DIR/setup-analysis-connector.sh"

echo "=== Setup complete ==="
