#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_DIR/backups/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
DATE=$(date +%Y%m%d_%H%M%S)

while [ "$#" -gt 0 ]; do
  case "$1" in --env) ENV_FILE=${2:-}; shift 2 ;; *) echo "用法: $0 [--env 文件]" >&2; exit 2 ;; esac
done

[ -f "$ENV_FILE" ] || { echo "找不到客户 .env" >&2; exit 1; }
compose() { docker compose --env-file "$ENV_FILE" -f "$DEPLOY_DIR/docker-compose.private.yml" "$@"; }
mkdir -p "$BACKUP_DIR"
chmod 700 "$DEPLOY_DIR/backups" "$BACKUP_DIR"
umask 077
temp="$BACKUP_DIR/mall_${DATE}.sql.gz.tmp"
final="$BACKUP_DIR/mall_${DATE}.sql.gz"

compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  | gzip > "$temp"
gzip -t "$temp"
mv "$temp" "$final"
chmod 600 "$final"
find "$BACKUP_DIR" -name 'mall_*.sql.gz' -mtime +"$RETENTION_DAYS" -delete
echo "数据库备份完成：$final"
