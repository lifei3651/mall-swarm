#!/bin/sh
set -eu

SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/mall-private-deploy-test.XXXXXX")
trap 'rm -rf "$TEST_ROOT"' EXIT HUP INT TERM

mkdir -p "$TEST_ROOT/document" "$TEST_ROOT/mall-distribution/target"
cp -R "$SOURCE_DIR" "$TEST_ROOT/document/private-deploy"
DEPLOY_DIR="$TEST_ROOT/document/private-deploy"
# 本机真实演练可能已生成被 Git 忽略的客户配置，测试副本必须从空配置开始。
rm -f "$DEPLOY_DIR/.env"
grep -q 'Content-Security-Policy' "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"
grep -q 'Permissions-Policy' "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"
grep -q 'autoindex off' "$DEPLOY_DIR/nginx/nginx.conf"
grep -q 'client_max_body_size 6m' "$DEPLOY_DIR/nginx/nginx.conf"
grep -q '\$request_method \$uri \$server_protocol' "$DEPLOY_DIR/nginx/nginx.conf"
if grep -q '"\$request"' "$DEPLOY_DIR/nginx/nginx.conf"; then
  echo "Nginx access log must not persist query strings" >&2
  exit 1
fi
if grep -q "connect-src 'self' https:" "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"; then
  echo "CSP connect-src must not allow arbitrary HTTPS origins" >&2
  exit 1
fi
grep -q "connect-src 'self'" "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"
grep -q 'set \$shop_surface public;' "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"
grep -q 'set \$shop_surface team;' "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"
[ "$(grep -c 'proxy_set_header X-Shop-Surface    \$shop_surface;' "$DEPLOY_DIR/nginx/includes/shop-api.conf")" = "4" ]
grep -q 'bootstrap-admin' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q '^DB_SSL_MODE=REQUIRED$' "$DEPLOY_DIR/customer.env.example"
[ -x "$DEPLOY_DIR/initdb/00_run_project_sql.sh" ]
sh -n "$DEPLOY_DIR/initdb/00_run_project_sql.sh"
grep -q '商城数据库基线不完整' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q 'dms_schema_baseline_marker' "$DEPLOY_DIR/initdb/00_run_project_sql.sh"
grep -q "'CUSTOMER_BONUS_DISABLED'" "$DEPLOY_DIR/initdb/99_customer_init.sql"
grep -Eq '\(1,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0,[[:space:]]*0\)' "$DEPLOY_DIR/initdb/99_customer_init.sql"
grep -q "title = '内部测试商城已开启'" "$DEPLOY_DIR/initdb/99_customer_init.sql"
grep -q "product_no IN ('LQ-SPU-001', 'LQ-SPU-002', 'LQ-SPU-003', 'LQ-SPU-004')" "$DEPLOY_DIR/initdb/99_customer_init.sql"
grep -q 'V202608170900__split_public_and_team_membership.sql' "$DEPLOY_DIR/scripts/run-migrations.sh"
grep -q 'bonus_version_count' "$DEPLOY_DIR/scripts/run-migrations.sh"
grep -q '客户必须且只能启用一个奖金程序' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q '20260714_erp_integration_upgrade.sql' "$DEPLOY_DIR/docker-compose.private.yml"
grep -q '20260808_add_shop_service_addresses.sql' "$DEPLOY_DIR/docker-compose.private.yml"
grep -q '数据库迁移总账不完整' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q 'command -v lsof' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q "127\\\\.0\\\\.0\\\\.1" "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q 'wait_healthy mysql' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q -- '--no-deps --force-recreate mall-distribution' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q -- '--no-deps --force-recreate nginx' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q 'RELEASE_GIT_COMMIT' "$DEPLOY_DIR/scripts/build-release.sh"
grep -q 'COPYFILE_DISABLE=1' "$DEPLOY_DIR/scripts/build-release.sh"
grep -q 'xattr -cr' "$DEPLOY_DIR/scripts/build-release.sh"
grep -q "name '__MACOSX'" "$DEPLOY_DIR/scripts/build-release.sh"
grep -q '客户 HTTPS 管理后台入口不可用' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q '构建提交与当前交付代码不一致' "$DEPLOY_DIR/scripts/security-postflight.sh"
for service in mysql redis mall-distribution nginx; do
  grep -A180 "^  $service:" "$DEPLOY_DIR/docker-compose.private.yml" | grep -q 'no-new-privileges:true'
  grep -A180 "^  $service:" "$DEPLOY_DIR/docker-compose.private.yml" | grep -q 'read_only: true'
  grep -A180 "^  $service:" "$DEPLOY_DIR/docker-compose.private.yml" | grep -q 'pids_limit:'
done
sh -n "$DEPLOY_DIR/scripts/bootstrap-admin.sh"
if LC_ALL=C grep -R -n -E '\$[A-Za-z_][A-Za-z0-9_]*[^ -~]' "$DEPLOY_DIR/scripts" --include='*.sh'; then
  echo "Shell 变量后必须使用花括号再连接中文标点" >&2
  exit 1
fi
printf 'FROM eclipse-temurin:17.0.19_10-jre-jammy\nUSER mall\n' > "$TEST_ROOT/mall-distribution/Dockerfile"

"$DEPLOY_DIR/scripts/prepare-env.sh" \
  --domain mall.customer.test \
  --team-domain team.customer.test \
  --admin-domain admin.customer.test \
  --ssh-cidr 203.0.113.10/32 \
  --project customer_test \
  --customer-name 测试客户公司 \
  --brand 测试客户商城 >/dev/null

[ "$(stat -c '%a' "$DEPLOY_DIR/.env" 2>/dev/null || stat -f '%Lp' "$DEPLOY_DIR/.env")" = "600" ]
for key in MYSQL_ROOT_PASSWORD DB_PASSWORD REDIS_PASSWORD DATA_ENCRYPTION_KEY; do
  value=$(awk -v key="$key" 'index($0, key "=") == 1 { print substr($0, length(key)+2) }' "$DEPLOY_DIR/.env")
  [ "${#value}" -ge 32 ]
  case "$value" in *change_me*) exit 1 ;; esac
done
[ "$(awk -F= '$1 == "DATA_ENCRYPTION_WRITE_ENABLED" { print $2 }' "$DEPLOY_DIR/.env")" = "true" ]
grep -q '^SHOP_LIVE_PROVIDER=EXTERNAL$' "$DEPLOY_DIR/.env"
grep -q '^SHOP_REAL_NAME_ENABLED=false$' "$DEPLOY_DIR/.env"
grep -q '^WECHAT_MINI_PROGRAM_ENABLED=false$' "$DEPLOY_DIR/.env"
grep -q '^WECHAT_MINI_PROGRAM_PHONE_AUTH_ENABLED=false$' "$DEPLOY_DIR/.env"
grep -q '^WECHAT_PAY_ENABLED=false$' "$DEPLOY_DIR/.env"
grep -q '^LIVE_PLAYBACK_ORIGIN=$' "$DEPLOY_DIR/.env"
grep -q 'LIVE_PLAYBACK_ORIGIN' "$DEPLOY_DIR/docker-compose.private.yml"
grep -q "media-src 'self' blob: \${LIVE_PLAYBACK_ORIGIN}" "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"
grep -q "connect-src 'self' \${LIVE_PLAYBACK_ORIGIN}" "$DEPLOY_DIR/nginx/conf.d/mall.conf.template"

mkdir -p "$DEPLOY_DIR/certs" "$DEPLOY_DIR/html/public" "$DEPLOY_DIR/html/team" "$DEPLOY_DIR/html/admin"
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -subj '/CN=mall.customer.test' \
  -addext 'subjectAltName=DNS:mall.customer.test,DNS:team.customer.test,DNS:admin.customer.test' \
  -keyout "$DEPLOY_DIR/certs/key.pem" \
  -out "$DEPLOY_DIR/certs/cert.pem" >/dev/null 2>&1
chmod 600 "$DEPLOY_DIR/certs/key.pem"
printf '<html>shop</html>\n' > "$DEPLOY_DIR/html/public/index.html"
printf '<html>team</html>\n' > "$DEPLOY_DIR/html/team/index.html"
printf '<html>admin</html>\n' > "$DEPLOY_DIR/html/admin/index.html"
printf '9.9.9\n' > "$TEST_ROOT/VERSION"
printf '{"version":"9.9.9"}\n' > "$DEPLOY_DIR/html/public/version.json"
printf '{"version":"9.9.9"}\n' > "$DEPLOY_DIR/html/team/version.json"
printf '{"version":"9.9.9"}\n' > "$DEPLOY_DIR/html/admin/version.json"
printf 'test jar\n' > "$TEST_ROOT/mall-distribution/target/mall-distribution-test.jar"

if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "未确认云安全组时预检不应通过" >&2
  exit 1
fi

"$DEPLOY_DIR/scripts/confirm-firewall.sh" \
  --ssh-cidr 203.0.113.10/32 \
  --evidence cloud-sg-test-001 >/dev/null
"$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null

sed -i.bak 's/^SHOP_LIVE_PROVIDER=.*/SHOP_LIVE_PROVIDER=TENCENT/' "$DEPLOY_DIR/.env"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "腾讯云直播缺少域名和密钥时预检不应通过" >&2
  exit 1
fi
mv "$DEPLOY_DIR/.env.bak" "$DEPLOY_DIR/.env"

sed -i.bak 's/^SHOP_REAL_NAME_ENABLED=.*/SHOP_REAL_NAME_ENABLED=true/' "$DEPLOY_DIR/.env"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "实名认证缺少腾讯云密钥时预检不应通过" >&2
  exit 1
fi
mv "$DEPLOY_DIR/.env.bak" "$DEPLOY_DIR/.env"

sed -i.bak 's/^WECHAT_MINI_PROGRAM_ENABLED=.*/WECHAT_MINI_PROGRAM_ENABLED=true/' "$DEPLOY_DIR/.env"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "微信小程序缺少客户 AppID 和 AppSecret 时预检不应通过" >&2
  exit 1
fi
mv "$DEPLOY_DIR/.env.bak" "$DEPLOY_DIR/.env"

cp "$DEPLOY_DIR/.env" "$DEPLOY_DIR/.env.wechat-test"
sed -i.bak 's/^WECHAT_MINI_PROGRAM_ENABLED=.*/WECHAT_MINI_PROGRAM_ENABLED=true/' "$DEPLOY_DIR/.env" && rm -f "$DEPLOY_DIR/.env.bak"
sed -i.bak 's/^WECHAT_MINI_PROGRAM_APP_ID=.*/WECHAT_MINI_PROGRAM_APP_ID=wx1234567890abcdef/' "$DEPLOY_DIR/.env" && rm -f "$DEPLOY_DIR/.env.bak"
sed -i.bak 's/^WECHAT_MINI_PROGRAM_APP_SECRET=.*/WECHAT_MINI_PROGRAM_APP_SECRET=strong-customer-app-secret/' "$DEPLOY_DIR/.env" && rm -f "$DEPLOY_DIR/.env.bak"
sed -i.bak 's/^WECHAT_PAY_ENABLED=.*/WECHAT_PAY_ENABLED=true/' "$DEPLOY_DIR/.env" && rm -f "$DEPLOY_DIR/.env.bak"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "微信支付缺少客户商户号、密钥和证书时预检不应通过" >&2
  exit 1
fi
mv "$DEPLOY_DIR/.env.wechat-test" "$DEPLOY_DIR/.env"

printf 'source map\n' > "$DEPLOY_DIR/html/public/app.js.map"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "存在 source map 时预检不应通过" >&2
  exit 1
fi
rm "$DEPLOY_DIR/html/public/app.js.map"

echo "private deployment security workflow tests passed"
