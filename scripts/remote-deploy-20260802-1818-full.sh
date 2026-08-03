#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260802-1818-full
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260802-1818-full

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"

EXPECTED_JAR=43d0d66cbe531f9bdd8b19172766a98422e54036198bcfd0732ce5706a6e8e01
EXPECTED_ADMIN=c2085d4ee32c0f52906b77bd096bd794518f580013cb5f8cb0289a35f9462da5
EXPECTED_SHOP=bcf9a77b39b0756e4c243bfd9f47dc5fd56fc27f7448eaa9ff3f1bed0b1d6a65
EXPECTED_ADMIN_INDEX=56cb195786f037b68357a819c1aeec68dcb3692d723792475836ec76ce0ba1fd
EXPECTED_SHOP_INDEX=f6af946ddcf2c7c9e2eef72446ca3abad360e77152db73c44a43aba34166cb99

MUTATED=0

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring previous application and web files" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

db_pass=$(database_password)
before_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
shipment_quantity_ready=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_shop_order_shipment' AND column_name='shipment_quantity'" \
  mall_distribution)
allocation_columns=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mall_distribution' AND table_name='dms_order_balance_allocation'" \
  mall_distribution)
unset db_pass
[[ "$shipment_quantity_ready" == 1 ]]
[[ "$allocation_columns" -ge 16 ]]

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .

MUTATED=1
systemctl stop lingqimall-distribution.service

install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"

# 保留近期旧哈希资源，避免已经打开的管理页面在发布瞬间无法加载分块。
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
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

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
echo "$EXPECTED_ADMIN_INDEX  $APP_ROOT/nginx/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $APP_ROOT/nginx/shop/index.html" | sha256sum -c -

captcha_json=$(curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop')
home_json=$(curl -fsS --max-time 12 https://lingqimall.com/api/shop/home)
shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q 'data:image/png;base64,' <<< "$captcha_json"
grep -q '"code":200' <<< "$home_json"
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"
echo "validation-stage=public-pages-ok"

admin_auth_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/admin-auth/me')
wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/wallet/flows')
shipment_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/admin/orders/shipment-template')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'http://lingqimall.com/')
[[ "$admin_auth_status" == 401 ]]
[[ "$wallet_status" == 401 ]]
[[ "$shipment_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]
echo "validation-stage=http-statuses admin=$admin_auth_status wallet=$wallet_status shipment=$shipment_status actuator=$actuator_status redirect=$http_status"

grep -R -Fq '编辑会员 ·' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '会员全景 ·' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '商城品牌与界面' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '价格、库存与规格' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '返回上一页' "$APP_ROOT/nginx/shop/assets"
grep -a -Fq 'OrderSpreadsheetService.class' "$APP_ROOT/app/mall-distribution.jar"
echo "validation-stage=release-assets-ok"

db_pass=$(database_password)
after_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_commission_record))" \
  mall_distribution)
unset db_pass
[[ "$before_counts" == "$after_counts" ]]
echo "validation-stage=database-ok core-counts=$after_counts"

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
security_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/)
admin_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/admin/)
grep -qi '^content-security-policy:' <<< "$security_headers"
grep -qi '^strict-transport-security:' <<< "$security_headers"
grep -qi '^cache-control:.*no-cache' <<< "$admin_headers"
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'
echo "validation-stage=security-and-hashes-ok"

# 成功后只删除七天前的旧分块，保留短期兼容窗口。
find "$APP_ROOT/nginx/admin/assets" -type f -mtime +7 -delete
find "$APP_ROOT/nginx/shop/assets" -type f -mtime +7 -delete

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
