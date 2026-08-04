#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.10
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.10

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"
VERSION_FILE="$RELEASE_DIR/VERSION"
MIGRATION="$RELEASE_DIR/tenant_display_config.sql"

EXPECTED_JAR=aca7ebab5a7801a443f20d4dd945506b7bb3dcf665660846b9b43cd097b6c032
EXPECTED_ADMIN=2b21bfc8ca1d8289d2facacd339d265af0b5283793103a6451e73e63b042a260
EXPECTED_SHOP=4b9d6b007e187731ac86adb626d2dcd648498fe3d4eff9e44a832041a64d7305
EXPECTED_VERSION_SHA=00251bee1d03f658c2324926785a996dd040dff16bf993b2c520a20edbba72c8
EXPECTED_MIGRATION_SHA=2b8f7dc513d610824a4883e793c4cf87d4b4e1fabe60231d7ad4cc9d0f641bb7
EXPECTED_OLD_VERSION=1.0.9
EXPECTED_NEW_VERSION=1.0.10

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
  unset db_pass
}

wait_health() {
  for _ in $(seq 1 60); do
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
    echo 'release failed; restoring previous application and web files' >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin-dist.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop-dist.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    wait_health || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -
echo "$EXPECTED_VERSION_SHA  $VERSION_FILE" | sha256sum -c -
echo "$EXPECTED_MIGRATION_SHA  $MIGRATION" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$VERSION_FILE")" == "$EXPECTED_NEW_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_OLD_VERSION" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
before_counts=$(database_counts)
echo "validation-stage=preflight current-version=$EXPECTED_OLD_VERSION core-counts=$before_counts"

db_pass=$(database_password)
schema_state=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
  "SELECT CONCAT(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_tenant' AND column_name='show_business_license'), ':',
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_tenant' AND column_name='faqs')
  )")
unset db_pass
echo "validation-stage=schema-before tenant-display-columns=$schema_state"

/usr/local/sbin/lingqimall-backup
backup_path=$(readlink -f "$APP_ROOT/backups/full/latest")
echo "validation-stage=full-backup path=$backup_path"

test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
tar -czf "$ROLLBACK_DIR/admin-dist.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop-dist.tar.gz" -C "$APP_ROOT/nginx/shop" .
(cd "$ROLLBACK_DIR" && sha256sum mall-distribution.jar VERSION admin-dist.tar.gz shop-dist.tar.gz > SHA256SUMS)

MUTATED=1
if [[ "$schema_state" == "0:0" ]]; then
  db_pass=$(database_password)
  MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user mall_distribution < "$MIGRATION"
  unset db_pass
elif [[ "$schema_state" != "1:1" ]]; then
  echo "unsupported tenant display schema state: $schema_state" >&2
  exit 1
fi
systemctl stop lingqimall-distribution.service
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$VERSION_FILE" "$APP_ROOT/VERSION"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$SHOP" -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"

systemctl start lingqimall-distribution.service
wait_health
nginx -t
systemctl reload nginx

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
echo "$EXPECTED_VERSION_SHA  $APP_ROOT/VERSION" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_NEW_VERSION" ]]
db_pass=$(database_password)
schema_after=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
  "SELECT CONCAT(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_tenant' AND column_name='show_business_license'), ':',
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_tenant' AND column_name='faqs')
  )")
unset db_pass
[[ "$schema_after" == "1:1" ]]
grep -R -Fq '余额转账' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '转账金额只能为整数' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '完成首单后开通团队业绩' "$APP_ROOT/nginx/shop/assets"

post_counts=$(database_counts)
[[ "$post_counts" == "$before_counts" ]]
echo "validation-stage=database-unchanged core-counts=$post_counts"

shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"
admin_auth_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' 'https://lingqimall.com/api/distribution/admin-auth/me')
wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' 'https://lingqimall.com/api/shop/wallet/flows')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' 'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' 'http://lingqimall.com/')
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

MUTATED=0
trap - EXIT
rm -rf -- "$ROLLBACK_DIR" "$RELEASE_DIR"
echo "release-success version=$EXPECTED_NEW_VERSION backup=$backup_path core-counts=$post_counts tenant-display-columns=$schema_after"
