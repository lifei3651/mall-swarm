#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260802-0907-full
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260802-0907-full

JAR="$RELEASE_DIR/mall-distribution.jar"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
MIGRATION="$RELEASE_DIR/20260802_order_balance_allocation.sql"

EXPECTED_JAR=35c97fc29e1c2b897786e19929050d489db48ecf4b94f08f585f83dce866301b
EXPECTED_SHOP=80200117a5638d23bd2e37dac9815511bba1512a174225d3290267afe6fe7035
EXPECTED_ADMIN=a10e1da5cd5e09adf3369de0ea12173a2d6f957d2a022e46c139cd9fe46d0ee9
EXPECTED_MIGRATION=f6adebd2335c17e76b042ff8387e6ee659e954cad72d2ad6fa474a2ee0ea69c1

MUTATED=0
TABLE_EXISTED_BEFORE=1

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring database compatibility and previous application" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/admin" || true
    if [[ "$TABLE_EXISTED_BEFORE" == 0 ]]; then
      db_pass=$(database_password)
      MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user \
        -e 'DROP TABLE IF EXISTS dms_order_balance_allocation' mall_distribution || true
      unset db_pass
    fi
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_MIGRATION  $MIGRATION" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

db_pass=$(database_password)
TABLE_EXISTED_BEFORE=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mall_distribution' AND table_name='dms_order_balance_allocation'" \
  mall_distribution)
before_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
target_accounts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM dms_shop_member m JOIN dms_agent a ON a.user_id=m.user_id WHERE m.id IN (1,5) AND m.status=1 AND a.status=1" \
  mall_distribution)
unset db_pass
[[ "$target_accounts" == 2 ]]

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .

MUTATED=1
systemctl stop lingqimall-distribution.service

db_pass=$(database_password)
MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user mall_distribution < "$MIGRATION"
unset db_pass

install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$SHOP" -C "$APP_ROOT/nginx/shop"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
chown -R www-data:www-data "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/admin"

systemctl start lingqimall-distribution.service
healthy=0
for _ in $(seq 1 60); do
  if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  sleep 2
done
[[ "$healthy" == 1 ]]
echo "validation-stage=backend-healthy"

nginx -t
systemctl reload nginx
echo "validation-stage=nginx-reloaded"

captcha_json=$(curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop')
home_json=$(curl -fsS --max-time 12 https://lingqimall.com/api/shop/home)
shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q 'data:image/png;base64,' <<< "$captcha_json"
grep -q '"code":200' <<< "$home_json"
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"
echo "validation-stage=public-pages-ok"

wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/wallet/flows')
audit_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/audit/order-finance/orders')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' 'http://lingqimall.com/')
[[ "$wallet_status" == 401 ]]
[[ "$audit_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]
echo "validation-stage=http-statuses wallet=$wallet_status audit=$audit_status actuator=$actuator_status redirect=$http_status"

grep -R -Fq '待满7天结算' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '已全部冲回/无需结算' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '奖金及其他明确入账' "$APP_ROOT/nginx/shop/assets"
echo "validation-stage=frontend-assets-ok"

db_pass=$(database_password)
table_ready=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_order_balance_allocation'" \
  mall_distribution)
after_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
unset db_pass
[[ "$table_ready" -ge 16 ]]
[[ "$before_counts" == "$after_counts" ]]
echo "validation-stage=database-ok columns=$table_ready core-counts=$after_counts"

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
security_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/)
grep -qi '^content-security-policy:' <<< "$security_headers"
grep -qi '^strict-transport-security:' <<< "$security_headers"
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'
echo "validation-stage=security-and-hashes-ok"

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
