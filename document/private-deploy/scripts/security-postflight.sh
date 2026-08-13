#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.private.yml"

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

command -v docker >/dev/null 2>&1 || fail "缺少 Docker"
command -v curl >/dev/null 2>&1 || fail "缺少 curl"
command -v ss >/dev/null 2>&1 || fail "缺少 ss，不能核对宿主机真实监听端口"

expected='mall-distribution mysql nginx redis'
running=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --services --status running | sort | tr '\n' ' ' | sed 's/ $//')
[ "$running" = "$expected" ] || fail "并非全部商城基座服务处于运行状态"

if ss -H -lnt | awk '{print $4}' | grep -E '(:|\])(3306|6379|8848|5672|15672|27017|9200|8086)$' | grep -q .; then
  fail "数据库、中间件或后端端口不应映射到宿主机任何地址"
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T nginx \
  wget -qO- http://mall-distribution:8086/actuator/health | grep -q '"status":"UP"' \
  || fail "Nginx 内部无法访问健康的商城后端"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mall-distribution \
  sh -c 'test -w /opt/lingqimall/uploads' \
  || fail "商城后端上传目录不可写"

domain=$(unquote "$(env_get CUSTOMER_DOMAIN)")
curl --fail --silent --show-error --max-time 15 --resolve "$domain:443:127.0.0.1" "https://$domain/" >/dev/null \
  || fail "客户 HTTPS 商城入口不可用或证书不匹配"
for path in /api/actuator/health /api/v3/api-docs /api/swagger-ui/index.html; do
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
  echo "version=$(tr -d '\n' < "$DEPLOY_DIR/../../VERSION")"
  echo "git_commit=$(git -C "$DEPLOY_DIR/../.." rev-parse HEAD 2>/dev/null || echo unavailable)"
  echo "ssh_allowed_cidr=$(unquote "$(env_get SSH_ALLOWED_CIDR)")"
  echo "cloud_firewall_evidence=$(unquote "$(env_get CLOUD_FIREWALL_EVIDENCE)")"
  echo "services=$expected"
  echo "public_ports=80,443"
  echo "mysql_binding=docker-internal-only"
  echo "internal_ports_not_public=6379,8086"
  echo "tls=PASS"
  echo "backend_health=UP"
  echo "upload_directory=writable"
  echo "sensitive_public_endpoints=404"
} > "$report"
chmod 600 "$report"
echo "部署后安全验收通过，报告已保存：$report"
