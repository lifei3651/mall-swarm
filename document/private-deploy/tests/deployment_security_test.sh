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
grep -q 'bootstrap-admin' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q '^DB_SSL_MODE=REQUIRED$' "$DEPLOY_DIR/customer.env.example"
[ -x "$DEPLOY_DIR/initdb/00_run_project_sql.sh" ]
sh -n "$DEPLOY_DIR/initdb/00_run_project_sql.sh"
grep -q '商城数据库基线不完整' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q 'dms_schema_baseline_marker' "$DEPLOY_DIR/initdb/00_run_project_sql.sh"
grep -q 'V202608170900__split_public_and_team_membership.sql' "$DEPLOY_DIR/scripts/run-migrations.sh"
grep -q '20260714_erp_integration_upgrade.sql' "$DEPLOY_DIR/docker-compose.private.yml"
grep -q '20260808_add_shop_service_addresses.sql' "$DEPLOY_DIR/docker-compose.private.yml"
grep -q '数据库迁移总账不完整' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q 'command -v lsof' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q "127\\\\.0\\\\.0\\\\.1" "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q 'wait_healthy mysql' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q -- '--no-deps --force-recreate mall-distribution' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q -- '--no-deps --force-recreate nginx' "$DEPLOY_DIR/scripts/deploy.sh"
grep -q 'RELEASE_GIT_COMMIT' "$DEPLOY_DIR/scripts/build-release.sh"
grep -q '客户 HTTPS 管理后台入口不可用' "$DEPLOY_DIR/scripts/security-postflight.sh"
grep -q '构建提交与当前交付代码不一致' "$DEPLOY_DIR/scripts/security-postflight.sh"
for service in mysql redis mall-distribution nginx; do
  grep -A80 "^  $service:" "$DEPLOY_DIR/docker-compose.private.yml" | grep -q 'no-new-privileges:true'
  grep -A80 "^  $service:" "$DEPLOY_DIR/docker-compose.private.yml" | grep -q 'read_only: true'
  grep -A80 "^  $service:" "$DEPLOY_DIR/docker-compose.private.yml" | grep -q 'pids_limit:'
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
for key in MYSQL_ROOT_PASSWORD DB_PASSWORD REDIS_PASSWORD SA_TOKEN_JWT_KEY DATA_ENCRYPTION_KEY; do
  value=$(awk -v key="$key" 'index($0, key "=") == 1 { print substr($0, length(key)+2) }' "$DEPLOY_DIR/.env")
  [ "${#value}" -ge 32 ]
  case "$value" in *change_me*) exit 1 ;; esac
done
[ "$(awk -F= '$1 == "DATA_ENCRYPTION_WRITE_ENABLED" { print $2 }' "$DEPLOY_DIR/.env")" = "true" ]

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

printf 'source map\n' > "$DEPLOY_DIR/html/public/app.js.map"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "存在 source map 时预检不应通过" >&2
  exit 1
fi
rm "$DEPLOY_DIR/html/public/app.js.map"

echo "private deployment security workflow tests passed"
