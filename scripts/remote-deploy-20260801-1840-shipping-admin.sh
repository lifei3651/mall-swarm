#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260801-1840-shipping-admin
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260801-1840-shipping-admin

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"

EXPECTED_JAR=24afeb03fbf004eb443e6e49fb600047e7bbd1ecefa94ecda69f2cdc7f01c1e1
EXPECTED_ADMIN=2a1d9a852c4b55aad21d3f88af82218a4db11d1fb3901f174879a36e86a36f21

MUTATED=0

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring previous backend and admin release" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

DB_CHECK_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
before_counts=$(MYSQL_PWD="$DB_CHECK_PASSWORD" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
unset DB_CHECK_PASSWORD

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .

MUTATED=1
systemctl stop lingqimall-distribution.service
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
chown -R www-data:www-data "$APP_ROOT/nginx/admin"

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

nginx -t
systemctl reload nginx

curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop' | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/api/shop/home | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/ | grep -q '<div id="app">'
curl -fsS --max-time 12 https://lingqimall.com/admin/ | grep -q '<div id="app">'

order_export_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/admin/orders/export')
shipment_template_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/admin/orders/shipment-template')
shipment_import_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' -X POST \
  'https://lingqimall.com/api/shop/admin/orders/shipments/import')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  https://lingqimall.com/api/actuator/health)
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' http://lingqimall.com/)
[[ "$order_export_status" == 401 ]]
[[ "$shipment_template_status" == 401 ]]
[[ "$shipment_import_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

grep -R -Fq '下载发货表' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '导入物流并发货' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '默认近30天（含今天）' "$APP_ROOT/nginx/admin/assets"

DB_CHECK_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
after_counts=$(MYSQL_PWD="$DB_CHECK_PASSWORD" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
unset DB_CHECK_PASSWORD
[[ "$before_counts" == "$after_counts" ]]

curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^content-security-policy:'
curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^strict-transport-security:'
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
