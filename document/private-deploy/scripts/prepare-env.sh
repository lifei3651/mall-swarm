#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
TEMPLATE="$DEPLOY_DIR/customer.env.example"
ENV_FILE="$DEPLOY_DIR/.env"
DOMAIN=""
ADMIN_DOMAIN=""
TEAM_DOMAIN=""
PROJECT=""
SSH_CIDR=""
CUSTOMER_NAME=""
BRAND_NAME=""

usage() {
  echo "用法: $0 --domain 公开商城域名 --team-domain 团队H5域名 --ssh-cidr 管理IP/掩码 [--admin-domain 后台域名] [--project 项目标识] [--customer-name 公司名] [--brand 商城名] [--env 文件]"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --domain) DOMAIN=${2:-}; shift 2 ;;
    --admin-domain) ADMIN_DOMAIN=${2:-}; shift 2 ;;
    --team-domain) TEAM_DOMAIN=${2:-}; shift 2 ;;
    --project) PROJECT=${2:-}; shift 2 ;;
    --ssh-cidr) SSH_CIDR=${2:-}; shift 2 ;;
    --customer-name) CUSTOMER_NAME=${2:-}; shift 2 ;;
    --brand) BRAND_NAME=${2:-}; shift 2 ;;
    --env) ENV_FILE=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[ -n "$DOMAIN" ] && [ -n "$TEAM_DOMAIN" ] && [ -n "$SSH_CIDR" ] && [ -n "$CUSTOMER_NAME" ] && [ -n "$BRAND_NAME" ] || { usage >&2; exit 2; }
printf '%s' "$DOMAIN" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' || { echo "域名格式不正确" >&2; exit 1; }
case "$DOMAIN" in *.example.com|example.com) echo "必须填写客户真实域名" >&2; exit 1 ;; esac
printf '%s' "$TEAM_DOMAIN" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' || { echo "团队H5域名格式不正确" >&2; exit 1; }
[ "$TEAM_DOMAIN" != "$DOMAIN" ] || { echo "公开商城域名与团队H5域名必须分开" >&2; exit 1; }
case "$TEAM_DOMAIN" in *.example.com|example.com) echo "必须填写客户真实团队H5域名" >&2; exit 1 ;; esac
printf '%s' "$SSH_CIDR" | grep -Eq '^[0-9A-Fa-f:.]+/[0-9]{1,3}$' || { echo "SSH 来源必须使用单个 IP 或网段 CIDR" >&2; exit 1; }
case "$SSH_CIDR" in 0.0.0.0/0|::/0) echo "禁止向全网开放 SSH" >&2; exit 1 ;; esac

[ -n "$ADMIN_DOMAIN" ] || ADMIN_DOMAIN=$DOMAIN
[ -n "$PROJECT" ] || PROJECT=$(printf '%s' "$DOMAIN" | tr '.-' '_' | tr '[:upper:]' '[:lower:]' | cut -c1-32)
printf '%s' "$PROJECT" | grep -Eq '^[a-z0-9][a-z0-9_-]{2,40}$' || { echo "项目标识只能使用小写字母、数字、下划线和短横线" >&2; exit 1; }
[ "${#CUSTOMER_NAME}" -le 64 ] && [ "${#BRAND_NAME}" -le 64 ] || { echo "客户公司名和商城名不能超过64字" >&2; exit 1; }
case "$CUSTOMER_NAME:$BRAND_NAME" in *'='*) echo "客户公司名和商城名不能包含等号" >&2; exit 1 ;; esac
original_bytes=$(printf '%s' "$CUSTOMER_NAME$BRAND_NAME" | wc -c | tr -d ' ')
single_line_bytes=$(printf '%s' "$CUSTOMER_NAME$BRAND_NAME" | tr -d '\r\n' | wc -c | tr -d ' ')
[ "$original_bytes" = "$single_line_bytes" ] || { echo "客户公司名和商城名不能包含换行" >&2; exit 1; }

[ -f "$TEMPLATE" ] || { echo "缺少环境变量模板" >&2; exit 1; }
[ ! -e "$ENV_FILE" ] || { echo "$ENV_FILE 已存在，为防止覆盖客户密钥已停止" >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "缺少 openssl，不能安全生成密钥" >&2; exit 1; }

umask 077
cp "$TEMPLATE" "$ENV_FILE"
chmod 600 "$ENV_FILE"

replace_value() {
  key=$1
  value=$2
  temp=$(mktemp "${ENV_FILE}.tmp.XXXXXX")
  awk -v key="$key" -v value="$value" '
    index($0, key "=") == 1 { print key "=" value; found=1; next }
    { print }
    END { if (!found) exit 42 }
  ' "$ENV_FILE" > "$temp" || { rm -f "$temp"; echo "模板缺少 $key" >&2; exit 1; }
  chmod 600 "$temp"
  mv "$temp" "$ENV_FILE"
}

replace_value COMPOSE_PROJECT_NAME "$PROJECT"
replace_value CUSTOMER_DOMAIN "$DOMAIN"
replace_value TEAM_DOMAIN "$TEAM_DOMAIN"
replace_value ADMIN_DOMAIN "$ADMIN_DOMAIN"
replace_value API_BASE_URL "https://$DOMAIN"
replace_value CORS_ORIGINS "https://$DOMAIN,https://$TEAM_DOMAIN,https://$ADMIN_DOMAIN"
replace_value SSH_ALLOWED_CIDR "$SSH_CIDR"
replace_value MYSQL_ROOT_PASSWORD "$(openssl rand -hex 32)"
replace_value DB_PASSWORD "$(openssl rand -hex 32)"
replace_value REDIS_PASSWORD "$(openssl rand -hex 32)"
replace_value SA_TOKEN_JWT_KEY "$(openssl rand -hex 48)"
replace_value DATA_ENCRYPTION_KEY "$(openssl rand -hex 32)"
replace_value CUSTOMER_NAME "$CUSTOMER_NAME"
replace_value CUSTOMER_BRAND_NAME "$BRAND_NAME"

echo "已生成受保护配置：${ENV_FILE}（权限 600）"
echo "下一步：在云控制台完成安全组后执行 scripts/confirm-firewall.sh，再运行 scripts/deploy.sh check。"
