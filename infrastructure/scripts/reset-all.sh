#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  Full System Reset"
echo "=========================================="
echo ""

# 1. Delete Debezium connectors (connector + pub/slot cleanup)
echo "[1/7] Deleting Debezium connectors..."
"$SCRIPT_DIR/debezium/delete-connectors.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 1: Debezium delete"
  exit 1
fi
echo ""

# 2. Delete Kafka topics & consumer groups
echo "[2/7] Deleting Kafka topics & consumer groups..."
"$SCRIPT_DIR/kafka/delete-topics.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 2: Kafka cleanup"
  exit 1
fi
echo ""

# 3. Delete OpenSearch index
echo "[3/7] Deleting OpenSearch index..."
"$SCRIPT_DIR/opensearch/delete-index.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 3: OpenSearch cleanup"
  exit 1
fi
echo ""

# 4. Drop all DB tables
echo "[4/7] Dropping all database tables..."
"$SCRIPT_DIR/db/drop-all.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 4: DB drop"
  exit 1
fi
echo ""

# 5. Run Flyway migration
echo "[5/7] Running Flyway migration..."
"$SCRIPT_DIR/db/migrate.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 5: Flyway migration"
  exit 1
fi
echo ""

# 6. Create OpenSearch index
echo "[6/7] Creating OpenSearch index..."
"$SCRIPT_DIR/opensearch/create-index.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 6: OpenSearch index creation"
  exit 1
fi
echo ""

# 7. Setup Debezium connectors
echo "[7/7] Setting up Debezium connectors..."
"$SCRIPT_DIR/debezium/setup-connectors.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 7: Debezium setup"
  exit 1
fi
echo ""

echo "=========================================="
echo "  Full System Reset Complete"
echo "=========================================="
