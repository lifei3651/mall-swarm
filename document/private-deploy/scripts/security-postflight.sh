#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$DEPLOY_DIR/../.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.private.yml"
MIGRATION_DIR="$ROOT_DIR/document/db/migrations"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) ENV_FILE=${2:-}; shift 2 ;;
    -h|--help) echo "用法: $0 [--env 文件]"; exit 0 ;;
    *) echo "参数错误" >&2; exit 2 ;;
  esac
done

fail() { echo "部署后验收失败：$*" >&2; exit 1; }
env_get() { awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key)+2); exit }' "$ENV_FILE"; }
unquote() { value=$1; case "$value" in \"*\") value=${value#\"}; value=${value%\"} ;; \'*\') value=${value#\'}; value=${value%\'} ;; esac; printf '%s' "$value"; }
mysql_query() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"'
}

command -v docker >/dev/null 2>&1 || fail "缺少 Docker"
command -v curl >/dev/null 2>&1 || fail "缺少 curl"
[ -d "$MIGRATION_DIR" ] || fail "缺少数据库迁移目录"

expected='mall-distribution mysql nginx redis'
running=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --services --status running | sort | tr '\n' ' ' | sed 's/ $//')
[ "$running" = "$expected" ] || fail "并非全部商城基座服务处于运行状态"

core_tables=$(mysql_query <<'SQL'
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('dms_tenant', 'dms_shop_product', 'dms_shop_order');
SQL
)
[ "$core_tables" = "3" ] || fail "商城数据库基线不完整，核心表数量为 $core_tables/3"
tenant_count=$(mysql_query <<'SQL'
SELECT COUNT(*) FROM dms_tenant WHERE id = 1;
SQL
)
[ "$tenant_count" = "1" ] || fail "商城基座租户 id=1 不存在"
migration_history=$(mysql_query <<'SQL'
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'dms_schema_migration_history';
SQL
)
[ "$migration_history" = "1" ] || fail "数据库迁移总账不存在"
failed_migrations=$(mysql_query <<'SQL'
SELECT COUNT(*) FROM dms_schema_migration_history WHERE success <> 1;
SQL
)
[ "$failed_migrations" = "0" ] || fail "数据库存在 $failed_migrations 条失败迁移记录"
expected_migrations=$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')
successful_migrations=$(mysql_query <<'SQL'
SELECT COUNT(*) FROM dms_schema_migration_history WHERE success = 1;
SQL
)
[ "$successful_migrations" = "$expected_migrations" ] \
  || fail "数据库迁移总账不完整，成功 $successful_migrations/$expected_migrations"
active_bonus_versions=$(mysql_query <<'SQL'
SELECT COUNT(*) FROM dms_commission_rule_version WHERE tenant_id = 1 AND status = 1;
SQL
)
[ "$active_bonus_versions" = "1" ] \
  || fail "客户必须且只能启用一个奖金程序，当前为 $active_bonus_versions 个"
promotion_join_mode=$(mysql_query <<'SQL'
SELECT promotion_join_mode FROM dms_tenant WHERE id = 1;
SQL
)
case "$promotion_join_mode" in
  DISABLED|AUTO_ON_INVITE|MANUAL_REVIEW|FIRST_PAID_ORDER) ;;
  *) fail "推广资格开通方式不正确：$promotion_join_mode" ;;
esac

if command -v ss >/dev/null 2>&1; then
  listening=$(ss -H -lnt | awk '{print $4}')
elif command -v lsof >/dev/null 2>&1; then
  listening=$(lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | awk 'NR > 1 { print $9 }')
else
  fail "缺少 ss 或 lsof，不能核对宿主机真实监听端口"
fi
public_internal_ports=$(printf '%s\n' "$listening" \
  | grep -E '(:|\])(3306|6379|8848|5672|15672|27017|9200|8086)$' \
  | grep -Ev '^(127\.0\.0\.1|\[::1\]|localhost):' || true)
if [ -n "$public_internal_ports" ]; then
  fail "数据库、中间件或后端端口不应监听宿主机公网地址"
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T nginx \
  wget -qO- http://mall-distribution:8086/actuator/health | grep -q '"status":"UP"' \
  || fail "Nginx 内部无法访问健康的商城后端"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mall-distribution \
  sh -c 'test -w /opt/lingqimall/uploads' \
  || fail "商城后端上传目录不可写"

domain=$(unquote "$(env_get CUSTOMER_DOMAIN)")
team_domain=$(unquote "$(env_get TEAM_DOMAIN)")
admin_domain=$(unquote "$(env_get ADMIN_DOMAIN)")
expected_version=$(tr -d '\n' < "$ROOT_DIR/VERSION")
expected_commit=$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || true)
validate_manifest() {
  label=$1
  application=$2
  manifest=$3
  printf '%s\n' "$manifest" | grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$expected_version\"" \
    || fail "$label 构建版本不是 $expected_version"
  printf '%s\n' "$manifest" | grep -q "\"application\"[[:space:]]*:[[:space:]]*\"$application\"" \
    || fail "$label 构建身份不正确"
  printf '%s\n' "$manifest" | grep -Eq '"gitCommit"[[:space:]]*:[[:space:]]*"[0-9a-f]{40}"' \
    || fail "$label 构建清单缺少 Git 提交身份"
  [ -z "$expected_commit" ] || printf '%s\n' "$manifest" | grep -q "\"gitCommit\"[[:space:]]*:[[:space:]]*\"$expected_commit\"" \
    || fail "$label 构建提交与当前交付代码不一致"
  printf '%s\n' "$manifest" | grep -Eq '"buildId"[[:space:]]*:[[:space:]]*"[^"[:space:]]+"' \
    || fail "$label 构建清单缺少构建批次"
}
curl --fail --silent --show-error --max-time 15 --resolve "$domain:443:127.0.0.1" "https://$domain/" >/dev/null \
  || fail "客户 HTTPS 商城入口不可用或证书不匹配"
curl --fail --silent --show-error --max-time 15 --resolve "$team_domain:443:127.0.0.1" "https://$team_domain/" >/dev/null \
  || fail "客户 HTTPS 团队H5入口不可用或证书不匹配"
curl --fail --silent --show-error --max-time 15 --resolve "$admin_domain:443:127.0.0.1" "https://$admin_domain/admin/" >/dev/null \
  || fail "客户 HTTPS 管理后台入口不可用或证书不匹配"
security_headers=$(curl --fail --silent --show-error --head --max-time 15 \
  --resolve "$domain:443:127.0.0.1" "https://$domain/")
printf '%s\n' "$security_headers" | grep -qi '^Content-Security-Policy:' \
  || fail "商城入口缺少 Content-Security-Policy"
printf '%s\n' "$security_headers" | grep -qi '^Permissions-Policy:' \
  || fail "商城入口缺少 Permissions-Policy"
printf '%s\n' "$security_headers" | grep -qi '^Strict-Transport-Security:' \
  || fail "商城入口缺少 Strict-Transport-Security"
public_manifest=$(curl --fail --silent --show-error --max-time 15 --resolve "$domain:443:127.0.0.1" "https://$domain/version.json") \
  || fail "公开域名没有返回公开商城构建"
team_manifest=$(curl --fail --silent --show-error --max-time 15 --resolve "$team_domain:443:127.0.0.1" "https://$team_domain/version.json") \
  || fail "团队域名没有返回团队H5构建"
admin_manifest=$(curl --fail --silent --show-error --max-time 15 --resolve "$admin_domain:443:127.0.0.1" "https://$admin_domain/admin/version.json") \
  || fail "后台域名没有返回管理后台构建"
validate_manifest "公开商城" "storefront-public" "$public_manifest"
validate_manifest "团队H5" "team-h5" "$team_manifest"
validate_manifest "管理后台" "admin" "$admin_manifest"
for path in /api/actuator/health /api/v3/api-docs /api/swagger-ui/index.html /.env /.git/config /phpmyadmin/; do
  code=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 --resolve "$domain:443:127.0.0.1" "https://$domain$path")
  [ "$code" = "404" ] || fail "$path 必须返回 404，当前为 $code"
done

mkdir -p "$DEPLOY_DIR/reports"
chmod 700 "$DEPLOY_DIR/reports"
umask 077
report="$DEPLOY_DIR/reports/security-postflight-$(date +%Y%m%d_%H%M%S).txt"
{
  echo "result=PASS"
  echo "checked_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "customer_domain=$domain"
  echo "team_domain=$team_domain"
  echo "admin_domain=$admin_domain"
  echo "version=$expected_version"
  echo "git_commit=$(git -C "$DEPLOY_DIR/../.." rev-parse HEAD 2>/dev/null || echo unavailable)"
  echo "ssh_allowed_cidr=$(unquote "$(env_get SSH_ALLOWED_CIDR)")"
  echo "cloud_firewall_evidence=$(unquote "$(env_get CLOUD_FIREWALL_EVIDENCE)")"
  echo "services=$expected"
  echo "public_ports=80,443"
  echo "mysql_binding=docker-internal-only"
  echo "internal_ports_not_public=6379,8086"
  echo "tls=PASS"
  echo "security_headers=PASS"
  echo "public_team_admin_builds=PASS"
  echo "common_scanner_paths=404"
  echo "backend_health=UP"
  echo "database_baseline=PASS"
  echo "database_migrations=PASS($successful_migrations/$expected_migrations)"
  echo "active_customer_bonus_policy=PASS(1)"
  echo "promotion_join_mode=PASS($promotion_join_mode)"
  echo "upload_directory=writable"
  echo "sensitive_public_endpoints=404"
} > "$report"
chmod 600 "$report"
echo "部署后安全验收通过，报告已保存：$report"
