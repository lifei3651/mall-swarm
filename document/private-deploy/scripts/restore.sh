#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <backup.sql.gz>"
  exit 1
fi

DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-mall}"

gunzip -c "$1" | mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"

echo "restore completed: $1"
