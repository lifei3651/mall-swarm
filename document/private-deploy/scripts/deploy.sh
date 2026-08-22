#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.private.yml"
ACTION=${1:-}
[ "$#" -gt 0 ] && shift

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}
env_get() { awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key)+2); exit }' "$ENV_FILE"; }
wait_healthy() {
  service=$1
  attempt=0
  while [ "$attempt" -lt 60 ]; do
    container=$(compose ps -q "$service" 2>/dev/null || true)
    if [ -n "$container" ]; then
      health=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)
      case "$health" in
        healthy|running) return 0 ;;
        unhealthy|exited|dead)
          compose logs --tail 80 "$service" >&2 || true
          echo "$service 启动失败，当前状态：$health" >&2
          return 1
          ;;
      esac
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  compose logs --tail 80 "$service" >&2 || true
  echo "$service 在 120 秒内未达到健康状态" >&2
  return 1
}

case "$ACTION" in
  build) exec "$SCRIPT_DIR/build-release.sh" "$@" ;;
  prepare) exec "$SCRIPT_DIR/prepare-env.sh" "$@" ;;
  firewall) exec "$SCRIPT_DIR/confirm-firewall.sh" "$@" ;;
  check) exec "$SCRIPT_DIR/security-preflight.sh" "$@" ;;
  verify) exec "$SCRIPT_DIR/security-postflight.sh" "$@" ;;
  bootstrap-admin) exec "$SCRIPT_DIR/bootstrap-admin.sh" "$@" ;;
  apply)
    [ "$#" -eq 0 ] || { echo "apply 不接受额外参数" >&2; exit 2; }
    "$SCRIPT_DIR/security-preflight.sh" --env "$ENV_FILE"

    mkdir -p "$DEPLOY_DIR/logs/nginx" "$DEPLOY_DIR/backups/mysql"
    chmod 700 "$DEPLOY_DIR/backups" "$DEPLOY_DIR/backups/mysql"

    mysql_container=$(compose ps -q mysql 2>/dev/null || true)
    if [ -n "$mysql_container" ] && [ "$(docker inspect -f '{{.State.Running}}' "$mysql_container")" = "true" ]; then
      umask 077
      stamp=$(date +%Y%m%d_%H%M%S)
      backup_tmp="$DEPLOY_DIR/backups/mysql/predeploy_${stamp}.sql.gz.tmp"
      backup_final="$DEPLOY_DIR/backups/mysql/predeploy_${stamp}.sql.gz"
      compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --all-databases' \
        | gzip > "$backup_tmp"
      gzip -t "$backup_tmp"
      mv "$backup_tmp" "$backup_final"
      chmod 600 "$backup_final"
      echo "升级前数据库备份完成：$backup_final"
    else
      project=$(env_get COMPOSE_PROJECT_NAME)
      if docker volume inspect "${project}_mysql_data" >/dev/null 2>&1; then
        echo "发现已有数据库卷但数据库容器未运行，无法完成升级前备份，已停止部署" >&2
        exit 1
      fi
      echo "未发现历史数据库卷，按首次安装继续。"
    fi

    # 首次部署先创建数据库基线；升级时保持数据卷，完成备份后再执行版本化迁移。
    compose up -d mysql redis
    wait_healthy mysql
    wait_healthy redis
    "$SCRIPT_DIR/run-migrations.sh"
    # 只重建应用和代理，不扰动数据库/Redis。构建会原子替换 html 目录，
    # Nginx 必须重建才能重新绑定新目录，避免升级后仍持有已删除的旧挂载节点。
    compose up -d --build --no-deps --force-recreate mall-distribution
    wait_healthy mall-distribution
    compose up -d --no-deps --force-recreate nginx
    wait_healthy nginx
    "$SCRIPT_DIR/security-postflight.sh" --env "$ENV_FILE"
    ;;
  *)
    echo "用法: $0 build|prepare|firewall|check|apply|bootstrap-admin|verify"
    exit 2
    ;;
esac
