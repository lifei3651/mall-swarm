#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${APP_ROOT:-/opt/lingqimall}"
BACKUP_ROOT="${BACKUP_ROOT:-$APP_ROOT/backups/full}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_USER="${DB_USER:-mall_user}"
DB_NAME="${DB_NAME:-mall_distribution}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
MIN_FREE_MB="${MIN_FREE_MB:-2048}"
OFFSITE_BACKUP_DIR="${OFFSITE_BACKUP_DIR:-}"
OFFSITE_RETENTION_DAYS="${OFFSITE_RETENTION_DAYS:-30}"
CONFIG_FILE="${CONFIG_FILE:-$APP_ROOT/config/application.yml}"
STAMP="$(date +%Y%m%d_%H%M%S)"
FINAL_DIR="$BACKUP_ROOT/$STAMP"
WORK_DIR="$BACKUP_ROOT/.${STAMP}.tmp"

install -d -m 0700 "$BACKUP_ROOT"
exec 9>"$BACKUP_ROOT/.backup.lock"
flock -n 9 || { echo "another backup is running"; exit 0; }

available_mb="$(df -Pm "$BACKUP_ROOT" | awk 'NR==2 {print $4}')"
if [[ "$available_mb" -lt "$MIN_FREE_MB" ]]; then
  echo "backup aborted: only ${available_mb}MB free, require ${MIN_FREE_MB}MB" >&2
  exit 1
fi

# 上次异常中断留下的临时目录不属于可恢复备份，超过一天后安全清理。
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '.20??????_??????.tmp' -mtime +1 -exec rm -rf {} +

DB_PASSWORD="${DB_PASSWORD:-$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$CONFIG_FILE")}" 
if [[ -z "$DB_PASSWORD" ]]; then
  echo "database password was not found" >&2
  exit 1
fi

cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT
install -d -m 0700 "$WORK_DIR"

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  -h"$DB_HOST" -u"$DB_USER" \
  --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces \
  "$DB_NAME" | gzip -9 > "$WORK_DIR/database.sql.gz"
gzip -t "$WORK_DIR/database.sql.gz"

tar -czf "$WORK_DIR/files-and-config.tar.gz" \
  --ignore-failed-read \
  -C / \
  "${APP_ROOT#/}/uploads" \
  "${APP_ROOT#/}/config" \
  "${APP_ROOT#/}/app/mall-distribution.jar" \
  "${APP_ROOT#/}/nginx/admin" \
  "${APP_ROOT#/}/nginx/shop" \
  etc/systemd/system/lingqimall-distribution.service \
  etc/systemd/system/lingqimall-distribution.service.d \
  etc/nginx/sites-enabled/lingqimall.conf
tar -tzf "$WORK_DIR/files-and-config.tar.gz" >/dev/null

(cd "$WORK_DIR" && sha256sum database.sql.gz files-and-config.tar.gz > SHA256SUMS)
{
  echo "created_at=$(date --iso-8601=seconds)"
  echo "database=$DB_NAME"
  echo "hostname=$(hostname)"
  echo "application_health=$(curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health || echo unavailable)"
} > "$WORK_DIR/MANIFEST"

chmod 0600 "$WORK_DIR"/*
mv "$WORK_DIR" "$FINAL_DIR"
trap - EXIT

find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20??????_??????' -mtime +"$RETENTION_DAYS" -exec rm -rf {} +
ln -sfn "$STAMP" "$BACKUP_ROOT/latest"

# 可选：OFFSITE_BACKUP_DIR应指向另一块挂载磁盘或远程文件系统。
# 复制完成后再次校验哈希，避免把不完整备份误认为可恢复备份。
if [[ -n "$OFFSITE_BACKUP_DIR" ]]; then
  install -d -m 0700 "$OFFSITE_BACKUP_DIR"
  offsite_final="$OFFSITE_BACKUP_DIR/$STAMP"
  offsite_work="$OFFSITE_BACKUP_DIR/.${STAMP}.tmp"
  rm -rf "$offsite_work"
  cp -a "$FINAL_DIR" "$offsite_work"
  (cd "$offsite_work" && sha256sum -c SHA256SUMS >/dev/null)
  mv "$offsite_work" "$offsite_final"
  find "$OFFSITE_BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20??????_??????' \
    -mtime +"$OFFSITE_RETENTION_DAYS" -exec rm -rf {} +
  ln -sfn "$STAMP" "$OFFSITE_BACKUP_DIR/latest"
fi

echo "backup completed: $FINAL_DIR"
