#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Delete All Kafka Topics & Consumer Groups ==="
echo ""

"$SCRIPT_DIR/delete-article-topic.sh"
"$SCRIPT_DIR/delete-analysis-topic.sh"
"$SCRIPT_DIR/delete-dlq-topic.sh"

echo "=== All Done ==="
