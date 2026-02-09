#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

OPENSEARCH_HOST="${OPENSEARCH_HOST:-localhost}"
OPENSEARCH_PORT="${OPENSEARCH_PORT:-9200}"
INDEX_NAME="${INDEX_NAME:-articles}"
PIPELINE_NAME="hybrid-search-pipeline"
BASE_URL="http://${OPENSEARCH_HOST}:${OPENSEARCH_PORT}"

echo "OpenSearch: ${BASE_URL}"
echo "Index:      ${INDEX_NAME}"

# --- Index ---

if curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/${INDEX_NAME}" | grep -q "200"; then
  echo "Index '${INDEX_NAME}' already exists. Skipping creation."
else
  echo "Creating index '${INDEX_NAME}'..."
  curl -s -X PUT "${BASE_URL}/${INDEX_NAME}" \
    -H 'Content-Type: application/json' \
    -d '{
    "settings": {
      "index": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "knn": true
      },
      "analysis": {
        "analyzer": {
          "nori": {
            "type": "custom",
            "tokenizer": "nori_tokenizer",
            "filter": ["lowercase", "nori_readingform"]
          }
        }
      }
    },
    "mappings": {
      "properties": {
        "articleId":        { "type": "keyword" },
        "sourceId":         { "type": "keyword" },
        "originId":         { "type": "keyword" },

        "title":            { "type": "text", "analyzer": "nori" },
        "content":          { "type": "text", "analyzer": "nori" },
        "keywords":         { "type": "text", "analyzer": "nori", "fields": { "keyword": { "type": "keyword" } } },
        "contentEmbedding": {
          "type": "knn_vector",
          "dimension": 128,
          "method": {
            "name": "hnsw",
            "space_type": "cosinesimil",
            "engine": "lucene"
          }
        },

        "incidentTypes": {
          "type": "nested",
          "properties": {
            "code": { "type": "keyword" },
            "name": { "type": "keyword" }
          }
        },
        "urgency": {
          "type": "object",
          "properties": {
            "name":  { "type": "keyword" },
            "level": { "type": "integer" }
          }
        },
        "incidentDate": { "type": "date" },

        "geoPoints": {
          "type": "nested",
          "properties": {
            "lat":      { "type": "double" },
            "lon":      { "type": "double" },
            "location": { "type": "geo_point" }
          }
        },
        "addresses": {
          "type": "nested",
          "properties": {
            "regionType":  { "type": "keyword" },
            "code":        { "type": "keyword" },
            "addressName": { "type": "text", "analyzer": "nori" },
            "depth1Name":  { "type": "keyword" },
            "depth2Name":  { "type": "keyword" },
            "depth3Name":  { "type": "keyword" }
          }
        },
        "jurisdictionCodes": { "type": "keyword" },

        "writtenAt":  { "type": "date" },
        "modifiedAt": { "type": "date" }
      }
    }
  }' | python3 -m json.tool
  echo "Index '${INDEX_NAME}' created successfully."
fi

# --- Search Pipeline ---

if curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/_search/pipeline/${PIPELINE_NAME}" | grep -q "200"; then
  echo "Pipeline '${PIPELINE_NAME}' already exists. Skipping creation."
else
  echo "Creating search pipeline '${PIPELINE_NAME}'..."
  curl -s -X PUT "${BASE_URL}/_search/pipeline/${PIPELINE_NAME}" \
    -H 'Content-Type: application/json' \
    -d '{
    "description": "Score normalization for hybrid (BM25 + kNN) search",
    "phase_results_processors": [
      {
        "normalization-processor": {
          "normalization": {
            "technique": "min_max"
          },
          "combination": {
            "technique": "arithmetic_mean",
            "parameters": {
              "weights": [0.3, 0.7]
            }
          }
        }
      }
    ]
  }' | python3 -m json.tool
  echo "Pipeline '${PIPELINE_NAME}' created successfully."
fi
