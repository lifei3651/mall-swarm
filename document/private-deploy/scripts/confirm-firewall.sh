#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
SSH_CIDR=""
EVIDENCE=""

usage() {
  echo "用法: $0 --ssh-cidr 管理IP/掩码 --evidence 云安全组截图或工单编号 [--env 文件]"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --ssh-cidr) SSH_CIDR=${2:-}; shift 2 ;;
    --evidence) EVIDENCE=${2:-}; shift 2 ;;
    --env) ENV_FILE=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[ -f "$ENV_FILE" ] || { echo "找不到 ${ENV_FILE}，请先生成客户配置" >&2; exit 1; }
printf '%s' "$SSH_CIDR" | grep -Eq '^[0-9A-Fa-f:.]+/[0-9]{1,3}$' || { echo "SSH 来源 CIDR 不正确" >&2; exit 1; }
case "$SSH_CIDR" in 0.0.0.0/0|::/0) echo "禁止向全网开放 SSH" >&2; exit 1 ;; esac
[ "${#EVIDENCE}" -ge 6 ] || { echo "必须填写可追溯的云安全组证据编号或文件名" >&2; exit 1; }
printf '%s' "$EVIDENCE" | grep -Eq '^[A-Za-z0-9._:/-]+$' || { echo "证据标识含不允许的字符" >&2; exit 1; }

umask 077
replace_value() {
  key=$1
  value=$2
  temp=$(mktemp "${ENV_FILE}.tmp.XXXXXX")
  awk -v key="$key" -v value="$value" 'index($0, key "=") == 1 { print key "=" value; found=1; next } { print } END { if (!found) exit 42 }' "$ENV_FILE" > "$temp" \
    || { rm -f "$temp"; echo "配置缺少 $key" >&2; exit 1; }
  chmod 600 "$temp"
  mv "$temp" "$ENV_FILE"
}

replace_value SSH_ALLOWED_CIDR "$SSH_CIDR"
replace_value CLOUD_FIREWALL_EVIDENCE "$EVIDENCE"
replace_value CLOUD_FIREWALL_CONFIRMED true
echo "已登记云安全组确认；部署程序仍会检查本机真实监听端口。"
