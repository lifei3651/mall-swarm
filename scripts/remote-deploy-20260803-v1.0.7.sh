#!/usr/bin/env bash
# 灵启商城 1.0.6 -> 1.0.7 发布：会员可调整为非会员（下级自动移交原上级）+ 退款后自动取消会员资格
# 更新后端 JAR、管理后台前端与版本标记；商城前端不变。
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.7
STAGE_DIR=/tmp/lingqimall-stage-v1.0.7
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.7

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN_PACKAGE="$RELEASE_DIR/admin-dist.tar.gz"
VERSION_FILE="$RELEASE_DIR/VERSION"

EXPECTED_JAR=39004e31de5e40a7a414fc141334c27a8cc639ba756ccf9be2b9f42d5c901801
EXPECTED_ADMIN_PACKAGE=7a17ab66835d86ca123fe032f19833284956ad9bca203da1b4c2ae8cc7966a3f
EXPECTED_VERSION_FILE=6d6b9230dd8b8aac527f8929b7c2fc1fa0365deccca856216de979d6f5c6a6fe
EXPECTED_ADMIN_INDEX=51877d13e3fc4ad00625677df22ff11af17d78c4a3b190675080c2542ef36e51
EXPECTED_SHOP_INDEX=10a1cf3fdba00f97f06bd7aa92a849a26b7d91a595c863e9af64f33a78b7c5f7
EXPECTED_OLD_VERSION=1.0.6
EXPECTED_NEW_VERSION=1.0.7

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
    echo "release failed; restoring application, admin web and version marker" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    systemctl start lingqimall-distribution.service || true
    wait_health || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" || true
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN_PACKAGE  $ADMIN_PACKAGE" | sha256sum -c -
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

test ! -e "$STAGE_DIR"
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$STAGE_DIR/admin" "$ROLLBACK_DIR"
tar -xzf "$ADMIN_PACKAGE" -C "$STAGE_DIR/admin"
echo "$EXPECTED_ADMIN_INDEX  $STAGE_DIR/admin/index.html" | sha256sum -c -

cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
(cd "$ROLLBACK_DIR" && sha256sum mall-distribution.jar admin.tar.gz VERSION > SHA256SUMS)

old_admin_entry=$(sed -n 's/.*src="\([^"]*\/assets\/index-[^"]*\.js\)".*/\1/p' "$APP_ROOT/nginx/admin/index.html" | head -n1)

MUTATED=1

# 后端
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
systemctl restart lingqimall-distribution.service
wait_health
echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -

# 管理后台前端：先合并资源，再替换入口
find "$STAGE_DIR/admin" -mindepth 1 -maxdepth 1 ! -name index.html -exec cp -a -- {} "$APP_ROOT/nginx/admin/" \;
install -m 0644 "$STAGE_DIR/admin/index.html" "$APP_ROOT/nginx/admin/index.html"
install -m 0644 "$VERSION_FILE" "$APP_ROOT/VERSION"
chown -R www-data:www-data "$APP_ROOT/nginx/admin"

nginx -t
systemctl reload nginx

echo "$EXPECTED_ADMIN_INDEX  $APP_ROOT/nginx/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $APP_ROOT/nginx/shop/index.html" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_NEW_VERSION" ]]

grep -R -Fq '灵启商城智慧经营驾驶舱' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '直属邀请会员' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '非会员（取消推广资格）' "$APP_ROOT/nginx/admin/assets"

if [[ -n "$old_admin_entry" ]]; then
  # 后台入口 src 为 /admin/assets/... 绝对路径，直接拼到 nginx 根目录即可。
  test -f "$APP_ROOT/nginx$old_admin_entry"
fi

post_counts=$(database_counts)
[[ "$post_counts" == "$before_counts" ]]
echo "validation-stage=database-unchanged core-counts=$post_counts"

shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"

new_admin_entry=$(sed -n 's/.*src="\([^"]*\/assets\/index-[^"]*\.js\)".*/\1/p' "$APP_ROOT/nginx/admin/index.html" | head -n1)
curl -fsS --max-time 12 -o /dev/null "https://lingqimall.com$new_admin_entry"

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
rm -rf -- "$STAGE_DIR" "$ROLLBACK_DIR" "$RELEASE_DIR"
echo "release-success version=$EXPECTED_NEW_VERSION backup=$backup_path core-counts=$post_counts"
