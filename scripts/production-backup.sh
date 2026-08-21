#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${APP_ROOT:-/opt/lingqimall}"
BACKUP_ROOT="${BACKUP_ROOT:-$APP_ROOT/backups/full}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_USER="${DB_USER:-}"
DB_NAME="${DB_NAME:-mall_distribution}"
DB_AUTH_MODE="${DB_AUTH_MODE:-auto}"
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

if [[ "$DB_AUTH_MODE" == auto ]]; then
  if [[ "$EUID" == 0 ]] && mysql --protocol=socket -uroot -NBe 'SELECT 1' >/dev/null 2>&1; then
    DB_AUTH_MODE=socket
    DB_USER="${DB_USER:-root}"
  else
    DB_AUTH_MODE=password
  fi
fi
if [[ "$DB_AUTH_MODE" == password ]]; then
  DB_USER="${DB_USER:-$(awk '/^[[:space:]]+username:/ {print $2; exit}' "$CONFIG_FILE")}"
  DB_PASSWORD="${DB_PASSWORD:-$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$CONFIG_FILE")}"
  if [[ -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
    echo "database credentials were not found" >&2
    exit 1
  fi
elif [[ "$DB_AUTH_MODE" == socket ]]; then
  DB_USER="${DB_USER:-root}"
else
  echo "DB_AUTH_MODE must be auto, password or socket" >&2
  exit 2
fi

cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT
install -d -m 0700 "$WORK_DIR"

if [[ "$DB_AUTH_MODE" == socket ]]; then
  mysqldump --protocol=socket -u"$DB_USER" \
    --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces \
    "$DB_NAME"
else
  MYSQL_PWD="$DB_PASSWORD" mysqldump \
    -h"$DB_HOST" -u"$DB_USER" --get-server-public-key \
    --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces \
    "$DB_NAME"
fi | gzip -9 > "$WORK_DIR/database.sql.gz"
gzip -t "$WORK_DIR/database.sql.gz"

backup_items=(
  "${APP_ROOT#/}/uploads"
  "${APP_ROOT#/}/config"
  "${APP_ROOT#/}/app/mall-distribution.jar"
  "${APP_ROOT#/}/nginx/admin"
  "${APP_ROOT#/}/nginx/shop"
  etc/systemd/system/lingqimall-distribution.service
  etc/systemd/system/lingqimall-backup.service
  etc/systemd/system/lingqimall-backup.timer
  usr/local/sbin/lingqimall-backup
)

# App/H5 拆分部署新增独立团队站点；旧的一体化部署可能没有该目录，按实际存在纳入备份。
[[ -d "$APP_ROOT/nginx/team" ]] && backup_items+=("${APP_ROOT#/}/nginx/team")

# 生产机既可能使用 Debian 的 sites-enabled，也可能使用 Rocky Linux 的
# conf.d。只收集实际存在的路径，避免迁机后“备份成功”却漏掉 Nginx 配置。
for optional_path in \
  etc/systemd/system/lingqimall-distribution.service.d \
  etc/nginx/nginx.conf \
  etc/nginx/conf.d \
  etc/nginx/sites-enabled \
  etc/nginx/lingqi-http-mode.conf \
  etc/letsencrypt; do
  [[ -e "/$optional_path" ]] && backup_items+=("$optional_path")
done

tar -czf "$WORK_DIR/files-and-config.tar.gz" -C / "${backup_items[@]}"
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
