#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"

if [ "$#" -lt 2 ] || [ "$1" != "--confirm" ]; then
  echo "恢复会覆盖当前数据库。用法: $0 --confirm <明确的备份.sql.gz> [--env 文件]" >&2
  exit 2
fi
BACKUP_FILE=$2
shift 2
while [ "$#" -gt 0 ]; do
  case "$1" in --env) ENV_FILE=${2:-}; shift 2 ;; *) echo "参数错误" >&2; exit 2 ;; esac
done

[ -f "$BACKUP_FILE" ] || { echo "备份文件不存在" >&2; exit 1; }
gzip -t "$BACKUP_FILE" || { echo "备份压缩包校验失败" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "找不到客户 .env" >&2; exit 1; }
compose() { docker compose --env-file "$ENV_FILE" -f "$DEPLOY_DIR/docker-compose.private.yml" "$@"; }

# 覆盖前自动保留当前数据库，禁止无备份恢复。
"$SCRIPT_DIR/backup.sh" --env "$ENV_FILE"
gunzip -c "$BACKUP_FILE" | compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
echo "数据库恢复完成：${BACKUP_FILE}；请立即执行 scripts/deploy.sh verify"
