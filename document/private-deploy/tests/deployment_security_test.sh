#!/bin/sh
set -eu

SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/mall-private-deploy-test.XXXXXX")
trap 'rm -rf "$TEST_ROOT"' EXIT HUP INT TERM

mkdir -p "$TEST_ROOT/document" "$TEST_ROOT/mall-distribution/target"
cp -R "$SOURCE_DIR" "$TEST_ROOT/document/private-deploy"
DEPLOY_DIR="$TEST_ROOT/document/private-deploy"
printf 'FROM eclipse-temurin:17.0.19_10-jre-jammy\nUSER mall\n' > "$TEST_ROOT/mall-distribution/Dockerfile"

"$DEPLOY_DIR/scripts/prepare-env.sh" \
  --domain mall.customer.test \
  --admin-domain admin.customer.test \
  --ssh-cidr 203.0.113.10/32 \
  --project customer_test \
  --customer-name 测试客户公司 \
  --brand 测试客户商城 >/dev/null

[ "$(stat -c '%a' "$DEPLOY_DIR/.env" 2>/dev/null || stat -f '%Lp' "$DEPLOY_DIR/.env")" = "600" ]
for key in MYSQL_ROOT_PASSWORD DB_PASSWORD REDIS_PASSWORD SA_TOKEN_JWT_KEY; do
  value=$(awk -v key="$key" 'index($0, key "=") == 1 { print substr($0, length(key)+2) }' "$DEPLOY_DIR/.env")
  [ "${#value}" -ge 32 ]
  case "$value" in *change_me*) exit 1 ;; esac
done

mkdir -p "$DEPLOY_DIR/certs" "$DEPLOY_DIR/html/admin"
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -subj '/CN=mall.customer.test' \
  -addext 'subjectAltName=DNS:mall.customer.test' \
  -keyout "$DEPLOY_DIR/certs/key.pem" \
  -out "$DEPLOY_DIR/certs/cert.pem" >/dev/null 2>&1
chmod 600 "$DEPLOY_DIR/certs/key.pem"
printf '<html>shop</html>\n' > "$DEPLOY_DIR/html/index.html"
printf '<html>admin</html>\n' > "$DEPLOY_DIR/html/admin/index.html"
printf '9.9.9\n' > "$TEST_ROOT/VERSION"
printf '{"version":"9.9.9"}\n' > "$DEPLOY_DIR/html/version.json"
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

printf 'source map\n' > "$DEPLOY_DIR/html/app.js.map"
if "$DEPLOY_DIR/scripts/security-preflight.sh" --offline >/dev/null 2>&1; then
  echo "存在 source map 时预检不应通过" >&2
  exit 1
fi
rm "$DEPLOY_DIR/html/app.js.map"

echo "private deployment security workflow tests passed"
