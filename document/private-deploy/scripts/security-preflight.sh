#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.private.yml"
OFFLINE=false

usage() {
  echo "用法: $0 [--env 文件] [--offline]"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) ENV_FILE=${2:-}; shift 2 ;;
    --offline) OFFLINE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

fail() { echo "安全预检失败：$*" >&2; exit 1; }
env_get() {
  awk -v key="$1" '
    index($0, key "=") == 1 { value=substr($0, length(key)+2); found=1 }
    END { if (found) print value; else exit 1 }
  ' "$ENV_FILE"
}
unquote() {
  value=$1
  case "$value" in
    \"*\") value=${value#\"}; value=${value%\"} ;;
    \'*\') value=${value#\'}; value=${value%\'} ;;
  esac
  printf '%s' "$value"
}
value_of() { unquote "$(env_get "$1" 2>/dev/null || true)"; }
require_value() { value=$(value_of "$1"); [ -n "$value" ] || fail "$1 不能为空"; }
require_secret() {
  value=$(value_of "$1")
  [ "${#value}" -ge "$2" ] || fail "$1 长度不足"
  case "$value" in *change_me*|*123456*|password|admin|root) fail "$1 仍是默认值或弱口令" ;; esac
}
file_mode() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1" 2>/dev/null
}

[ -f "$ENV_FILE" ] || fail "找不到 ${ENV_FILE}，请先运行 prepare-env.sh"
[ "$(file_mode "$ENV_FILE")" = "600" ] || fail "$ENV_FILE 权限必须是 600"

if ! awk '
  /^[[:space:]]*($|#)/ { next }
  /^[A-Z][A-Z0-9_]*=/ { key=$0; sub(/=.*/, "", key); count[key]++; next }
  { bad=1 }
  END { for (key in count) if (count[key] != 1) bad=1; exit bad }
' "$ENV_FILE"; then
  fail ".env 必须每行只有一个不重复的 KEY=VALUE，禁止 shell 命令"
fi

[ "$(value_of DEPLOYMENT_ENV)" = "production" ] || fail "DEPLOYMENT_ENV 必须是 production"
require_value COMPOSE_PROJECT_NAME
printf '%s' "$(value_of COMPOSE_PROJECT_NAME)" | grep -Eq '^[a-z0-9][a-z0-9_-]{2,40}$' || fail "COMPOSE_PROJECT_NAME 格式不正确"
require_value CUSTOMER_DOMAIN
require_value TEAM_DOMAIN
require_value ADMIN_DOMAIN
require_value CUSTOMER_NAME
require_value CUSTOMER_BRAND_NAME
require_value CORS_ORIGINS
require_value SSH_ALLOWED_CIDR
require_value CLOUD_FIREWALL_EVIDENCE
require_secret MYSQL_ROOT_PASSWORD 32
require_secret DB_PASSWORD 32
require_secret REDIS_PASSWORD 32
require_secret SA_TOKEN_JWT_KEY 48
data_encryption_key=$(value_of DATA_ENCRYPTION_KEY)
printf '%s' "$data_encryption_key" | grep -Eq '^[0-9A-Fa-f]{64}$' || fail "DATA_ENCRYPTION_KEY 必须是64位十六进制随机密钥"
[ "$(value_of DATA_ENCRYPTION_WRITE_ENABLED)" = "true" ] || fail "客户正式部署必须启用 DATA_ENCRYPTION_WRITE_ENABLED=true"
[ "$(value_of DB_HOST)" = "mysql" ] && [ "$(value_of DB_NAME)" = "mall_distribution" ] || fail "当前独立部署的数据库必须使用内部 mysql/mall_distribution"
[ "$(value_of DB_SSL_MODE)" = "REQUIRED" ] || fail "当前生产模板必须使用 DB_SSL_MODE=REQUIRED；外部数据库应使用 VERIFY_IDENTITY"
[ "$(value_of REDIS_HOST)" = "redis" ] && [ "$(value_of REDIS_PORT)" = "6379" ] || fail "当前独立部署的Redis必须使用内部服务"
for key in MYSQL_CPU_LIMIT REDIS_CPU_LIMIT APP_CPU_LIMIT NGINX_CPU_LIMIT; do
  value=$(value_of "$key")
  printf '%s' "$value" | grep -Eq '^[0-9]+([.][0-9]+)?$' || fail "$key 必须是正数"
  printf '%s' "$value" | grep -Eq '^0+([.]0+)?$' && fail "$key 必须大于0"
done
for key in MYSQL_MEMORY_LIMIT REDIS_MEMORY_LIMIT APP_MEMORY_LIMIT NGINX_MEMORY_LIMIT; do
  printf '%s' "$(value_of "$key")" | grep -Eq '^[1-9][0-9]*[mMgG]$' || fail "$key 必须使用正数加 m/g 单位"
done
for key in MYSQL_PIDS_LIMIT REDIS_PIDS_LIMIT APP_PIDS_LIMIT NGINX_PIDS_LIMIT; do
  printf '%s' "$(value_of "$key")" | grep -Eq '^[1-9][0-9]*$' || fail "$key 必须是正整数"
done

domain=$(value_of CUSTOMER_DOMAIN)
team_domain=$(value_of TEAM_DOMAIN)
admin_domain=$(value_of ADMIN_DOMAIN)
case "$domain" in *.example.com|example.com) fail "必须替换示例域名" ;; esac
printf '%s' "$domain" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' || fail "客户域名格式不正确"
printf '%s' "$team_domain" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' || fail "团队H5域名格式不正确"
[ "$team_domain" != "$domain" ] || fail "公开商城域名与团队H5域名必须分开"
case "$(value_of CUSTOMER_NAME):$(value_of CUSTOMER_BRAND_NAME)" in *客户公司名称*|*客户商城名称*|*待后台配置*) fail "必须填写客户真实公司名和商城名" ;; esac
printf '%s' "$(value_of CUSTOMER_THEME_COLOR)" | grep -Eq '^#[0-9A-Fa-f]{6}$' || fail "客户主题色必须是六位十六进制颜色"
printf '%s' "$(value_of CUSTOMER_PRODUCT_TEMPLATE)" | grep -Eq '^[a-z0-9_-]{2,32}$' || fail "商品模板标识格式不正确"
for key in SHOP_WITHDRAWAL_DAILY_MAX_COUNT SHOP_WITHDRAWAL_MONTHLY_MAX_COUNT; do
  printf '%s' "$(value_of "$key")" | grep -Eq '^[1-9][0-9]*$' || fail "$key 必须是正整数"
done
for key in SHOP_WITHDRAWAL_DAILY_MAX_AMOUNT SHOP_WITHDRAWAL_MONTHLY_MAX_AMOUNT; do
  printf '%s' "$(value_of "$key")" | grep -Eq '^[0-9]+([.][0-9]{1,2})?$' || fail "$key 必须是最多两位小数的正金额"
  printf '%s' "$(value_of "$key")" | grep -Eq '^0+([.]0{1,2})?$' && fail "$key 必须大于0"
done
cors=$(value_of CORS_ORIGINS)
case "$cors" in *\**|*http://*) fail "CORS 只能列出客户 HTTPS 来源，禁止通配符和 HTTP" ;; esac
case ",$cors," in *,https://"$domain",*) : ;; *) fail "CORS 必须包含公开商城域名" ;; esac
case ",$cors," in *,https://"$team_domain",*) : ;; *) fail "CORS 必须包含团队H5域名" ;; esac

[ "$(value_of CLOUD_FIREWALL_CONFIRMED)" = "true" ] || fail "必须先在云控制台只放行 80/443，并限制 SSH 来源，再执行 confirm-firewall.sh"
ssh_cidr=$(value_of SSH_ALLOWED_CIDR)
case "$ssh_cidr" in 0.0.0.0/0|::/0) fail "禁止向全网开放 SSH" ;; esac
printf '%s' "$ssh_cidr" | grep -Eq '^[0-9A-Fa-f:.]+/[0-9]{1,3}$' || fail "SSH_ALLOWED_CIDR 必须是 CIDR"

alipay=$(value_of ALIPAY_ENABLED)
case "$alipay" in
  true)
    require_value ALIPAY_APP_ID
    require_value ALIPAY_SELLER_ID
    require_secret ALIPAY_PRIVATE_KEY 32
    require_secret ALIPAY_PUBLIC_KEY 32
    for key in ALIPAY_NOTIFY_URL ALIPAY_RETURN_URL; do
      value=$(value_of "$key")
      case "$value" in "https://$domain"/*) : ;; *) fail "$key 必须使用客户 HTTPS 主域名" ;; esac
    done
    ;;
  false) : ;;
  *) fail "ALIPAY_ENABLED 只能是 true 或 false" ;;
esac

sms=$(value_of SMS_PROVIDER_ENABLED)
case "$sms" in
  true)
    require_value SMS_ALIYUN_ACCESS_KEY_ID
    require_secret SMS_ALIYUN_ACCESS_KEY_SECRET 16
    for key in SMS_ALIYUN_SIGN_NAME SMS_TEMPLATE_REGISTER SMS_TEMPLATE_LOGIN SMS_TEMPLATE_RESET_PASSWORD SMS_TEMPLATE_TRANSFER SMS_TEMPLATE_WITHDRAW SMS_TEMPLATE_PAYMENT SMS_TEMPLATE_PAYMENT_PASSWORD; do
      require_value "$key"
    done
    ;;
  false) [ "$(value_of PAYMENT_LARGE_AMOUNT_VERIFY_ENABLED)" != "true" ] || fail "启用大额短信验证前必须启用真实短信" ;;
  *) fail "SMS_PROVIDER_ENABLED 只能是 true 或 false" ;;
esac

command -v openssl >/dev/null 2>&1 || fail "缺少 OpenSSL，无法检查TLS证书"
command -v python3 >/dev/null 2>&1 || fail "缺少 Python 3，无法检查证书域名和Compose安全边界"
[ -f "$DEPLOY_DIR/certs/cert.pem" ] || fail "缺少 certs/cert.pem"
[ -f "$DEPLOY_DIR/certs/key.pem" ] || fail "缺少 certs/key.pem"
case "$(file_mode "$DEPLOY_DIR/certs/key.pem")" in 400|600) : ;; *) fail "TLS 私钥权限必须是 400 或 600" ;; esac
openssl x509 -in "$DEPLOY_DIR/certs/cert.pem" -noout -checkend 2592000 >/dev/null 2>&1 || fail "TLS 证书无效或将在 30 天内过期"
if ! python3 - "$DEPLOY_DIR/certs/cert.pem" "$domain" "$team_domain" "$admin_domain" >/dev/null 2>&1 <<'PY'
import ssl
import sys
import warnings

certificate = ssl._ssl._test_decode_cert(sys.argv[1])
with warnings.catch_warnings():
    warnings.simplefilter("ignore", DeprecationWarning)
    for hostname in sys.argv[2:]:
        ssl.match_hostname(certificate, hostname)
PY
then
  fail "TLS 证书必须同时覆盖公开商城、团队H5和后台域名"
fi

[ -f "$DEPLOY_DIR/html/public/index.html" ] || fail "缺少公开商城生产构建 html/public/index.html"
[ -f "$DEPLOY_DIR/html/team/index.html" ] || fail "缺少团队H5生产构建 html/team/index.html"
[ -f "$DEPLOY_DIR/html/admin/index.html" ] || fail "缺少后台生产构建 html/admin/index.html"
if find "$DEPLOY_DIR/html" -type f -name '*.map' -print -quit | grep -q .; then
  fail "生产静态资源中禁止包含 source map"
fi
find "$DEPLOY_DIR/../../mall-distribution/target" -maxdepth 1 -type f -name '*.jar' -print -quit 2>/dev/null | grep -q . \
  || fail "缺少 mall-distribution 后端 Jar，请先完成生产构建"
version=$(tr -d '\n' < "$DEPLOY_DIR/../../VERSION")
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$DEPLOY_DIR/html/public/version.json" \
  || fail "商城构建版本与根 VERSION 不一致"
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$DEPLOY_DIR/html/team/version.json" \
  || fail "团队H5构建版本与根 VERSION 不一致"
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$DEPLOY_DIR/html/admin/version.json" \
  || fail "后台构建版本与根 VERSION 不一致"

grep -Eq '^[[:space:]]*env_file:' "$COMPOSE_FILE" && fail "禁止使用 env_file 向所有容器注入整套密钥"
grep -q 'docker.sock' "$COMPOSE_FILE" && fail "禁止挂载 Docker socket"
grep -q '^FROM eclipse-temurin:17\.0\.19_10-jre-jammy$' "$DEPLOY_DIR/../../mall-distribution/Dockerfile" \
  || fail "后端运行时镜像必须使用已测试的明确版本"
grep -q '^USER mall$' "$DEPLOY_DIR/../../mall-distribution/Dockerfile" || fail "后端容器必须使用非root用户"
[ -x "$DEPLOY_DIR/initdb/00_run_project_sql.sh" ] \
  || fail "MySQL 初始化入口必须保留可执行权限，否则会生成空数据库"
sh -n "$DEPLOY_DIR/initdb/00_run_project_sql.sh" \
  || fail "MySQL 初始化入口脚本语法错误"

if [ "$OFFLINE" = "false" ]; then
  command -v docker >/dev/null 2>&1 || fail "缺少 Docker"
  docker compose version >/dev/null 2>&1 || fail "缺少 Docker Compose v2"
  rendered=$(mktemp "${TMPDIR:-/tmp}/mall-compose.XXXXXX.json")
  trap 'rm -f "$rendered"' EXIT HUP INT TERM
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --format json > "$rendered" \
    || fail "Docker Compose 配置无法展开"
  python3 "$SCRIPT_DIR/validate_compose.py" "$rendered" || exit 1
fi

echo "客户配置、强密钥、云安全组确认、TLS、构建产物和部署模板预检通过"
