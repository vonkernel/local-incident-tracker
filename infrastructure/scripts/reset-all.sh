#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  Full System Reset"
echo "=========================================="
echo ""

# 1. Delete Debezium connectors (connector + pub/slot cleanup)
echo "[1/5] Deleting Debezium connectors..."
"$SCRIPT_DIR/debezium/delete-connectors.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 1: Debezium delete"
  exit 1
fi
echo ""

# 2. Delete Kafka topics & consumer groups
echo "[2/5] Deleting Kafka topics & consumer groups..."
"$SCRIPT_DIR/kafka/delete-topics.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 2: Kafka cleanup"
  exit 1
fi
echo ""

# 3. Drop all DB tables
echo "[3/5] Dropping all database tables..."
"$SCRIPT_DIR/db/drop-all.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 3: DB drop"
  exit 1
fi
echo ""

# 4. Run Flyway migration
echo "[4/5] Running Flyway migration..."
"$SCRIPT_DIR/db/migrate.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 4: Flyway migration"
  exit 1
fi
echo ""

# 5. Setup Debezium connectors
echo "[5/5] Setting up Debezium connectors..."
"$SCRIPT_DIR/debezium/setup-connectors.sh"
if [ $? -ne 0 ]; then
  echo "Failed at step 5: Debezium setup"
  exit 1
fi
echo ""

echo "=========================================="
echo "  Full System Reset Complete"
echo "=========================================="
