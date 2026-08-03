#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260802-163245-shipment-quantity
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260802-163245-shipment-quantity

JAR="$RELEASE_DIR/lingqimall-distribution-shipment-qty.jar"
ADMIN="$RELEASE_DIR/lingqimall-admin-shipment-qty.tar.gz"
SHOP="$RELEASE_DIR/lingqimall-shop-shipment-qty.tar.gz"
MIGRATION="$RELEASE_DIR/20260802_order_multi_shipment.sql"

EXPECTED_JAR=85055211aa07a8b4299f6f4f7a87ffda9b6b16e6a760488a19d8955541bf84ae
EXPECTED_ADMIN=dab1c055928023deee386118af9be0a97bcd184b6052fa543ddaefaead5857a8
EXPECTED_SHOP=ca0e2ec5509781557a465cf1edeb6bc0cb9a0a72da4bd47216fb3952d8832726
EXPECTED_MIGRATION=07ff5353ca1f3c5db4e41e3ed27b4530cdaff45a16772dfb5d790438fb7ece7f

MUTATED=0
TABLE_EXISTED_BEFORE=0

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring database and previous application" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    db_pass=$(database_password)
    if [[ "$TABLE_EXISTED_BEFORE" == 0 ]]; then
      MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user \
        -e 'DROP TABLE IF EXISTS dms_shop_order_shipment' mall_distribution || true
    else
      MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user \
        -e 'DROP TABLE IF EXISTS dms_shop_order_shipment' mall_distribution || true
      MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user \
        mall_distribution < "$ROLLBACK_DIR/dms_shop_order_shipment.sql" || true
    fi
    unset db_pass
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -
echo "$EXPECTED_MIGRATION  $MIGRATION" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

db_pass=$(database_password)
TABLE_EXISTED_BEFORE=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mall_distribution' AND table_name='dms_shop_order_shipment'" \
  mall_distribution)
before_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
unset db_pass

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
if [[ "$TABLE_EXISTED_BEFORE" == 1 ]]; then
  db_pass=$(database_password)
  MYSQL_PWD="$db_pass" mysqldump --ssl-mode=REQUIRED --no-tablespaces --single-transaction \
    -h 127.0.0.1 -u mall_user mall_distribution dms_shop_order_shipment \
    > "$ROLLBACK_DIR/dms_shop_order_shipment.sql"
  unset db_pass
fi

MUTATED=1
systemctl stop lingqimall-distribution.service

db_pass=$(database_password)
MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user \
  mall_distribution < "$MIGRATION"
unset db_pass

install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$SHOP" -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"

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

template_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/admin/orders/shipment-template')
wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/wallet/flows')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' 'http://lingqimall.com/')
[[ "$template_status" == 401 ]]
[[ "$wallet_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]
echo "validation-stage=http-statuses template=$template_status wallet=$wallet_status actuator=$actuator_status redirect=$http_status"

grep -R -Fq '错误行会单独跳过' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '发货数量' "$APP_ROOT/nginx/shop/assets"
grep -a -Fq 'DmsShopOrderShipmentMapper.xml' "$APP_ROOT/app/mall-distribution.jar"
echo "validation-stage=release-assets-ok"

db_pass=$(database_password)
table_columns=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_shop_order_shipment'" \
  mall_distribution)
quantity_column=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_shop_order_shipment' AND column_name='shipment_quantity' AND data_type='int'" \
  mall_distribution)
legacy_missing=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM dms_shop_order o WHERE o.delivery_company IS NOT NULL AND TRIM(o.delivery_company) <> '' AND o.delivery_no IS NOT NULL AND TRIM(o.delivery_no) <> '' AND NOT EXISTS (SELECT 1 FROM dms_shop_order_shipment s WHERE s.order_id=o.id AND s.delivery_company=o.delivery_company AND s.delivery_no=o.delivery_no)" \
  mall_distribution)
invalid_quantities=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM dms_shop_order_shipment WHERE shipment_quantity <= 0" mall_distribution)
after_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
unset db_pass
[[ "$table_columns" -ge 11 ]]
[[ "$quantity_column" == 1 ]]
[[ "$legacy_missing" == 0 ]]
[[ "$invalid_quantities" == 0 ]]
[[ "$before_counts" == "$after_counts" ]]
echo "validation-stage=database-ok columns=$table_columns legacy-missing=$legacy_missing invalid-quantities=$invalid_quantities core-counts=$after_counts"

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
