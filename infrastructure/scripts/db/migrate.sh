#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../../.."

echo "=== Flyway Migration ==="
echo ""

cd "$PROJECT_ROOT" || { echo "Failed to navigate to project root: $PROJECT_ROOT"; exit 1; }

./gradlew persistence:flywayMigrate

if [ $? -eq 0 ]; then
  echo ""
  echo "  Migration complete."
else
  echo ""
  echo "  Migration failed."
  exit 1
fi
