#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$SCRIPT_DIR/../../.env"; set +a

echo "=== Drop All Tables & Sequences ==="
echo ""

docker exec "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO $POSTGRES_USER;"

if [ $? -eq 0 ]; then
  echo "  Schema reset complete."
else
  echo "  Failed to reset schema."
  exit 1
fi
