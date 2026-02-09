#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

OPENSEARCH_HOST="${OPENSEARCH_HOST:-localhost}"
OPENSEARCH_PORT="${OPENSEARCH_PORT:-9200}"
INDEX_NAME="${INDEX_NAME:-articles}"
PIPELINE_NAME="hybrid-search-pipeline"
BASE_URL="http://${OPENSEARCH_HOST}:${OPENSEARCH_PORT}"

echo "=== OpenSearch Cleanup ==="
echo "OpenSearch: ${BASE_URL}"
echo ""

# --- Delete Search Pipeline ---
echo "[1/2] Deleting search pipeline '${PIPELINE_NAME}'..."
status_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${BASE_URL}/_search/pipeline/${PIPELINE_NAME}")
if [ "$status_code" = "200" ]; then
  echo "  Deleted: ${PIPELINE_NAME}"
elif [ "$status_code" = "404" ]; then
  echo "  Not found (skip): ${PIPELINE_NAME}"
else
  echo "  Failed (HTTP $status_code)"
fi
echo ""

# --- Delete Index ---
echo "[2/2] Deleting index '${INDEX_NAME}'..."
status_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${BASE_URL}/${INDEX_NAME}")
if [ "$status_code" = "200" ]; then
  echo "  Deleted: ${INDEX_NAME}"
elif [ "$status_code" = "404" ]; then
  echo "  Not found (skip): ${INDEX_NAME}"
else
  echo "  Failed (HTTP $status_code)"
fi
echo ""

echo "=== OpenSearch Cleanup Complete ==="
