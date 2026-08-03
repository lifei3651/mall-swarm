#!/usr/bin/env bash
# 灵启商城 1.0.5 -> 1.0.6 发布：短信验证码错误次数限制 + 每手机号每日发送上限
# 仅更新后端 JAR 与版本标记，前端不变。
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.6
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.6

JAR="$RELEASE_DIR/mall-distribution.jar"
VERSION_FILE="$RELEASE_DIR/VERSION"

EXPECTED_JAR=5ef714fc8c3db282a953dce24753438b0d8f63a8890b3a6d316bfae2304ae1a3
EXPECTED_VERSION_FILE=51c18dc8694861beb47a67894e61044864214435389f6d3a274704d9566ff96c
EXPECTED_ADMIN_INDEX=a3c40a0fdfc796a515141e052b688fa7a90174939976cfa25c7a7824d514141c
EXPECTED_SHOP_INDEX=10a1cf3fdba00f97f06bd7aa92a849a26b7d91a595c863e9af64f33a78b7c5f7
EXPECTED_OLD_VERSION=1.0.5
EXPECTED_NEW_VERSION=1.0.6

MUTATED=0

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

database_counts() {
  local db_pass
  db_pass=$(database_password)
  MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
    "SELECT CONCAT(
        (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=0), ':',
        (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=1), ':',
        (SELECT COUNT(*) FROM dms_shop_order), ':',
        (SELECT COUNT(*) FROM dms_commission_record), ':',
        (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
        (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
        (SELECT COUNT(*) FROM dms_shop_product), ':',
        (SELECT COUNT(*) FROM dms_shop_category), ':',
        (SELECT COUNT(*) FROM dms_admin_user WHERE status=1)
    )" mall_distribution
}

wait_health() {
  local i
  for i in $(seq 1 90); do
    if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "release failed; restoring application and version marker" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    systemctl start lingqimall-distribution.service || true
    wait_health || true
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_VERSION_FILE  $VERSION_FILE" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$VERSION_FILE")" == "$EXPECTED_NEW_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_OLD_VERSION" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
wait_health

before_counts=$(database_counts)
echo "validation-stage=preflight version=$EXPECTED_OLD_VERSION core-counts=$before_counts"

/usr/local/sbin/lingqimall-backup
backup_path=$(readlink -f "$APP_ROOT/backups/full/latest")
echo "validation-stage=full-backup path=$backup_path"

test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
(cd "$ROLLBACK_DIR" && sha256sum mall-distribution.jar VERSION > SHA256SUMS)

MUTATED=1

install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
systemctl restart lingqimall-distribution.service
wait_health
echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
install -m 0644 "$VERSION_FILE" "$APP_ROOT/VERSION"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_NEW_VERSION" ]]

# 前端本次未变更，核对线上入口文件仍为 1.0.5 发布的版本
echo "$EXPECTED_ADMIN_INDEX  $APP_ROOT/nginx/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $APP_ROOT/nginx/shop/index.html" | sha256sum -c -

post_counts=$(database_counts)
[[ "$post_counts" == "$before_counts" ]]
echo "validation-stage=database-unchanged core-counts=$post_counts"

shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"

admin_auth_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/admin-auth/me')
wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/wallet/flows')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'http://lingqimall.com/')
[[ "$admin_auth_status" == 401 ]]
[[ "$wallet_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

security_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/)
admin_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/admin/)
grep -qi '^content-security-policy:' <<< "$security_headers"
grep -qi '^strict-transport-security:' <<< "$security_headers"
grep -qi '^cache-control:.*no-cache' <<< "$security_headers"
grep -qi '^cache-control:.*no-cache' <<< "$admin_headers"

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'

MUTATED=0
trap - EXIT
rm -rf -- "$ROLLBACK_DIR" "$RELEASE_DIR"
echo "release-success version=$EXPECTED_NEW_VERSION backup=$backup_path core-counts=$post_counts"
