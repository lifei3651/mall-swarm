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
for key in SHOP_WITHDRAWAL_MANUAL_REVIEW_THRESHOLD; do
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

wechat_mini=$(value_of WECHAT_MINI_PROGRAM_ENABLED)
wechat_phone=$(value_of WECHAT_MINI_PROGRAM_PHONE_AUTH_ENABLED)
wechat_subscribe=$(value_of WECHAT_MINI_PROGRAM_SUBSCRIBE_MESSAGE_ENABLED)
wechat_shipping=$(value_of WECHAT_MINI_PROGRAM_SHIPPING_INFO_ENABLED)
case "$wechat_phone" in true|false) : ;; *) fail "WECHAT_MINI_PROGRAM_PHONE_AUTH_ENABLED 只能是 true 或 false" ;; esac
case "$wechat_subscribe" in true|false) : ;; *) fail "WECHAT_MINI_PROGRAM_SUBSCRIBE_MESSAGE_ENABLED 只能是 true 或 false" ;; esac
case "$wechat_shipping" in true|false) : ;; *) fail "WECHAT_MINI_PROGRAM_SHIPPING_INFO_ENABLED 只能是 true 或 false" ;; esac
case "$wechat_mini" in
  true)
    printf '%s' "$(value_of WECHAT_MINI_PROGRAM_APP_ID)" | grep -Eq '^wx[0-9A-Za-z]{16}$' \
      || fail "启用微信小程序前必须填写合法的客户 AppID"
    require_secret WECHAT_MINI_PROGRAM_APP_SECRET 16
    printf '%s' "$(value_of WECHAT_MINI_PROGRAM_PRIVACY_VERSION)" | grep -Eq '^[A-Za-z0-9_.-]{3,64}$' \
      || fail "微信小程序隐私政策版本格式不正确"
    [ "$(value_of WECHAT_MINI_PROGRAM_STATE)" = "formal" ] \
      || fail "客户生产部署的微信小程序状态必须是 formal"
    if [ "$wechat_subscribe" = "true" ]; then
      for key in WECHAT_SUBSCRIBE_TEMPLATE_ORDER_SHIPPED WECHAT_SUBSCRIBE_TEMPLATE_AFTER_SALE_UPDATED WECHAT_SUBSCRIBE_TEMPLATE_REFUND_RESULT WECHAT_SUBSCRIBE_TEMPLATE_WITHDRAW_PAID; do
        printf '%s' "$(value_of "$key")" | grep -Eq '^[A-Za-z0-9_-]{20,128}$' \
          || fail "启用微信订阅消息前必须填写合法模板编号：$key"
      done
      for key in WECHAT_SUBSCRIBE_ORDER_SHIPPED_STATUS_KEY WECHAT_SUBSCRIBE_AFTER_SALE_STATUS_KEY WECHAT_SUBSCRIBE_REFUND_STATUS_KEY WECHAT_SUBSCRIBE_WITHDRAW_STATUS_KEY; do
        printf '%s' "$(value_of "$key")" | grep -Eq '^phrase[0-9]{1,3}$' \
          || fail "微信订阅消息状态字段必须使用 phrase 类型：$key"
      done
      for key in WECHAT_SUBSCRIBE_ORDER_SHIPPED_TIME_KEY WECHAT_SUBSCRIBE_AFTER_SALE_TIME_KEY WECHAT_SUBSCRIBE_REFUND_TIME_KEY WECHAT_SUBSCRIBE_WITHDRAW_TIME_KEY; do
        printf '%s' "$(value_of "$key")" | grep -Eq '^(time|date)[0-9]{1,3}$' \
          || fail "微信订阅消息时间字段必须使用 time 或 date 类型：$key"
      done
      for key in WECHAT_SUBSCRIBE_ORDER_SHIPPED_REMARK_KEY WECHAT_SUBSCRIBE_AFTER_SALE_REMARK_KEY WECHAT_SUBSCRIBE_REFUND_REMARK_KEY WECHAT_SUBSCRIBE_WITHDRAW_REMARK_KEY; do
        printf '%s' "$(value_of "$key")" | grep -Eq '^thing[0-9]{1,3}$' \
          || fail "微信订阅消息备注字段必须使用 thing 类型：$key"
      done
    fi
    ;;
  false)
    [ "$wechat_phone" = "false" ] && [ "$wechat_subscribe" = "false" ] && [ "$wechat_shipping" = "false" ] \
      || fail "微信小程序登录关闭时手机号、订阅消息和发货同步都必须关闭"
    ;;
  *) fail "WECHAT_MINI_PROGRAM_ENABLED 只能是 true 或 false" ;;
esac

wechat_pay=$(value_of WECHAT_PAY_ENABLED)
case "$wechat_pay" in
  true)
    [ "$wechat_mini" = "true" ] || fail "启用微信支付前必须启用微信小程序登录"
    printf '%s' "$(value_of WECHAT_PAY_MCH_ID)" | grep -Eq '^[0-9]{8,32}$' || fail "微信支付商户号格式不正确"
    printf '%s' "$(value_of WECHAT_PAY_MERCHANT_SERIAL_NUMBER)" | grep -Eq '^[0-9A-Fa-f]{16,64}$' \
      || fail "微信支付商户证书序列号格式不正确"
    require_value WECHAT_PAY_PUBLIC_KEY_ID
    api_v3_key=$(value_of WECHAT_PAY_API_V3_KEY)
    [ "${#api_v3_key}" -eq 32 ] || fail "WECHAT_PAY_API_V3_KEY 必须正好32字节"
    cert_dir=$(value_of WECHAT_PAY_CERT_DIR)
    case "$cert_dir" in /*) : ;; *) cert_dir="$DEPLOY_DIR/${cert_dir#./}" ;; esac
    [ -d "$cert_dir" ] || fail "微信支付证书目录不存在：$cert_dir"
    private_key="$cert_dir/apiclient_key.pem"
    public_key="$cert_dir/wechatpay_public_key.pem"
    [ -f "$private_key" ] && [ -f "$public_key" ] || fail "微信支付私钥或公钥PEM文件缺失"
    [ "$(file_mode "$private_key")" = "600" ] || fail "微信支付商户API私钥权限必须是600"
    case "$(value_of WECHAT_PAY_PRIVATE_KEY_PATH)" in /run/secrets/wechat-pay/apiclient_key.pem) : ;; *) fail "微信支付容器私钥路径不可修改" ;; esac
    case "$(value_of WECHAT_PAY_PUBLIC_KEY_PATH)" in /run/secrets/wechat-pay/wechatpay_public_key.pem) : ;; *) fail "微信支付容器公钥路径不可修改" ;; esac
    for key in WECHAT_PAY_NOTIFY_URL WECHAT_PAY_REFUND_NOTIFY_URL; do
      value=$(value_of "$key")
      case "$value" in "https://$domain"/api/pay/wechat/*) : ;; *) fail "$key 必须使用客户HTTPS主域名和固定微信支付回调路径" ;; esac
    done
    ;;
  false) : ;;
  *) fail "WECHAT_PAY_ENABLED 只能是 true 或 false" ;;
esac
[ "$wechat_shipping" = "false" ] || [ "$wechat_pay" = "true" ] \
  || fail "启用微信发货信息同步前必须完成微信支付配置"

payout=$(value_of WITHDRAWAL_PAYOUT_ENABLED)
payout_alipay=$(value_of WITHDRAWAL_PAYOUT_ALIPAY_ENABLED)
payout_wechat=$(value_of WITHDRAWAL_PAYOUT_WECHAT_ENABLED)
for value in "$payout" "$payout_alipay" "$payout_wechat"; do
  case "$value" in true|false) : ;; *) fail "奖金转账开关只能是 true 或 false" ;; esac
done
if [ "$payout" = "false" ]; then
  [ "$payout_alipay" = "false" ] && [ "$payout_wechat" = "false" ] \
    || fail "奖金转账总开关关闭时各渠道也必须关闭"
else
  [ "$payout_alipay" = "true" ] || [ "$payout_wechat" = "true" ] \
    || fail "启用奖金转账时必须选择至少一个官方渠道"
  [ "$(value_of DATA_ENCRYPTION_WRITE_ENABLED)" = "true" ] \
    || fail "启用奖金转账前必须开启敏感字段加密"
  [ "$payout_alipay" = "false" ] || [ "$alipay" = "true" ] \
    || fail "启用支付宝奖金转账前必须完成支付宝配置"
  if [ "$payout_wechat" = "true" ]; then
    [ "$wechat_pay" = "true" ] && [ "$wechat_mini" = "true" ] \
      || fail "启用微信奖金转账前必须完成微信支付和小程序配置"
    require_value WITHDRAWAL_PAYOUT_WECHAT_SCENE_ID
    require_value WITHDRAWAL_PAYOUT_WECHAT_REPORT_INFO_TYPE
    require_value WITHDRAWAL_PAYOUT_WECHAT_REPORT_INFO_CONTENT
  fi
fi

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

real_name=$(value_of SHOP_REAL_NAME_ENABLED)
case "$real_name" in
  true)
    require_value TENCENT_FACEID_SECRET_ID
    require_secret TENCENT_FACEID_SECRET_KEY 16
    require_value TENCENT_FACEID_REGION
    [ "$(value_of TENCENT_FACEID_ENDPOINT)" = "faceid.tencentcloudapi.com" ] || fail "实名认证只允许腾讯云官方 FaceID 接口域名"
    ;;
  false) : ;;
  *) fail "SHOP_REAL_NAME_ENABLED 只能是 true 或 false" ;;
esac

notification_enabled=$(value_of EXTERNAL_NOTIFICATION_ENABLED)
notification_worker=$(value_of EXTERNAL_NOTIFICATION_WORKER_ENABLED)
notification_sms=$(value_of NOTIFICATION_SMS_ALIYUN_ENABLED)
case "$notification_sms" in true|false) : ;; *) fail "NOTIFICATION_SMS_ALIYUN_ENABLED 只能是 true 或 false" ;; esac
for key in NOTIFICATION_MOCK_ENABLED NOTIFICATION_MOCK_APP_PUSH_ENABLED NOTIFICATION_MOCK_MINI_PROGRAM_ENABLED; do
  [ "$(value_of "$key")" = "false" ] || fail "客户生产部署禁止启用 App/小程序模拟通知适配器：$key"
done
[ "$wechat_subscribe" = "false" ] || { [ "$notification_enabled" = "true" ] && [ "$notification_worker" = "true" ]; } \
  || fail "启用微信订阅消息前必须开启外部通知总门禁和发送器"
case "$notification_enabled" in
  false)
    [ "$notification_worker" = "false" ] || fail "外部通知关闭时发送器必须关闭"
    [ "$notification_sms" = "false" ] || fail "外部通知关闭时通知短信适配器必须关闭"
    ;;
  true)
    [ "$notification_worker" = "true" ] || fail "外部通知启用时必须显式启用发送器"
    [ "$notification_sms" = "true" ] || [ "$wechat_subscribe" = "true" ] \
      || fail "外部通知总门禁开启时必须至少启用一个真实通知渠道"
    if [ "$notification_sms" = "true" ]; then
      require_value NOTIFICATION_SMS_ALIYUN_ACCESS_KEY_ID
      require_secret NOTIFICATION_SMS_ALIYUN_ACCESS_KEY_SECRET 16
      require_secret NOTIFICATION_SMS_ALIYUN_RECEIPT_SECRET 16
      for key in NOTIFICATION_SMS_ALIYUN_SIGN_NAME NOTIFICATION_SMS_TEMPLATE_LOGIN_PASSWORD_CHANGED NOTIFICATION_SMS_TEMPLATE_PAY_PASSWORD_CHANGED NOTIFICATION_SMS_TEMPLATE_PHONE_CHANGED NOTIFICATION_SMS_TEMPLATE_ORDER_SHIPPED NOTIFICATION_SMS_TEMPLATE_AFTER_SALE_UPDATED NOTIFICATION_SMS_TEMPLATE_REFUND_RESULT; do
        require_value "$key"
      done
    fi
    ;;
  *) fail "EXTERNAL_NOTIFICATION_ENABLED 只能是 true 或 false" ;;
esac

live_provider=$(value_of SHOP_LIVE_PROVIDER)
live_playback_origin=$(value_of LIVE_PLAYBACK_ORIGIN)
if [ -n "$live_playback_origin" ]; then
  printf '%s' "$live_playback_origin" | grep -Eq '^https://[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' \
    || fail "LIVE_PLAYBACK_ORIGIN 必须是单个无路径的 HTTPS 来源"
fi
case "$live_provider" in
  EXTERNAL) : ;;
  TENCENT)
    for key in TENCENT_LIVE_PUSH_DOMAIN TENCENT_LIVE_PLAY_DOMAIN TENCENT_LIVE_APP_NAME; do
      require_value "$key"
    done
    printf '%s' "$(value_of TENCENT_LIVE_PUSH_DOMAIN)" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' || fail "TENCENT_LIVE_PUSH_DOMAIN 格式不正确"
    printf '%s' "$(value_of TENCENT_LIVE_PLAY_DOMAIN)" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}$' || fail "TENCENT_LIVE_PLAY_DOMAIN 格式不正确"
    [ "$live_playback_origin" = "https://$(value_of TENCENT_LIVE_PLAY_DOMAIN)" ] \
      || fail "腾讯云直播的 LIVE_PLAYBACK_ORIGIN 必须等于 https://TENCENT_LIVE_PLAY_DOMAIN"
    require_secret TENCENT_LIVE_PUSH_AUTH_KEY 16
    require_secret TENCENT_LIVE_CALLBACK_AUTH_KEY 16
    printf '%s' "$(value_of TENCENT_LIVE_CREDENTIAL_SECONDS)" | grep -Eq '^[0-9]+$' || fail "TENCENT_LIVE_CREDENTIAL_SECONDS 必须是整数"
    [ "$(value_of TENCENT_LIVE_CREDENTIAL_SECONDS)" -ge 600 ] && [ "$(value_of TENCENT_LIVE_CREDENTIAL_SECONDS)" -le 86400 ] \
      || fail "TENCENT_LIVE_CREDENTIAL_SECONDS 必须在600到86400秒之间"
    ;;
  *) fail "SHOP_LIVE_PROVIDER 只能是 EXTERNAL 或 TENCENT" ;;
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
