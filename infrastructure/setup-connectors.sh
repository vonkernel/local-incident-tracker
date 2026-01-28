#!/bin/bash

# Wait for Debezium Connect to be ready
echo "Waiting for Debezium Connect to be ready..."
until curl -f http://localhost:18083/ > /dev/null 2>&1; do
  echo "Debezium Connect not ready yet. Retrying in 5 seconds..."
  sleep 5
done

echo "Debezium Connect is ready!"
echo ""

# Register PostgreSQL connector for articles table
echo "Registering PostgreSQL connector for articles table..."
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:18083/connectors/ -d '{
  "name": "lit-articles-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "lit-maindb",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "lit_maindb",
    "database.server.name": "lit",
    "table.include.list": "public.article",
    "topic.prefix": "lit",
    "plugin.name": "pgoutput",
    "publication.autocreate.mode": "filtered",
    "publication.name": "dbz_articles_pub",
    "slot.name": "debezium_articles",
    "heartbeat.interval.ms": "10000"
  }
}'

echo ""
echo ""

# Register PostgreSQL connector for analysis_result_outbox table (outbox pattern)
echo "Registering PostgreSQL connector for analysis_result_outbox table..."
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:18083/connectors/ -d '{
  "name": "lit-analysis-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "lit-maindb",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "lit_maindb",
    "database.server.name": "lit",
    "table.include.list": "public.analysis_result_outbox",
    "topic.prefix": "lit",
    "plugin.name": "pgoutput",
    "publication.autocreate.mode": "filtered",
    "publication.name": "dbz_analysis_pub",
    "slot.name": "debezium_analysis",
    "heartbeat.interval.ms": "10000"
  }
}'

echo ""
echo ""

# Check connector status
echo "Checking connector status..."
curl -s http://localhost:18083/connectors/lit-articles-connector/status | jq '.'
echo ""
curl -s http://localhost:18083/connectors/lit-analysis-connector/status | jq '.'

echo ""
echo "Connectors registered successfully!"
echo "Topics created:"
echo "  - lit.public.article (article-events)"
echo "  - lit.public.analysis_result_outbox (analysis-events)"
