#!/bin/sh
set -eu

BACKUP_DIR="${BACKUP_DIR:-./backups/mysql}"
DATE="$(date +%Y%m%d_%H%M%S)"
DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-mall}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

mkdir -p "$BACKUP_DIR"

mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers "$DB_NAME" \
  | gzip > "$BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz"

find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "backup completed: $BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz"
