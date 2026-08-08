#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.31
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.31
BACKUP_PATH=""
MUTATED=0

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "release failed; restoring previous backend, frontend and version" >&2
    systemctl stop lingqimall-distribution.service || true
    [[ -s "$ROLLBACK_DIR/mall-distribution.jar" ]] && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    [[ -d "$ROLLBACK_DIR/admin" ]] && { rm -rf -- "$APP_ROOT/nginx/admin"; mv "$ROLLBACK_DIR/admin" "$APP_ROOT/nginx/admin"; } || true
    [[ -d "$ROLLBACK_DIR/shop" ]] && { rm -rf -- "$APP_ROOT/nginx/shop"; mv "$ROLLBACK_DIR/shop" "$APP_ROOT/nginx/shop"; } || true
    [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

[[ -s "$RELEASE_DIR/mall-distribution.jar" ]]
[[ -s "$RELEASE_DIR/admin.tar.gz" ]]
[[ -s "$RELEASE_DIR/shop.tar.gz" ]]
[[ -s "$RELEASE_DIR/VERSION" ]]
[[ -s "$RELEASE_DIR/20260808_add_product_safety_stock.sql" ]]
[[ -s "$RELEASE_DIR/20260808_add_shop_service_addresses.sql" ]]
[[ -s "$RELEASE_DIR/20260808_add_after_sale_return_workflow.sql" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "1.0.31" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.30" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

/usr/local/sbin/lingqimall-backup
BACKUP_PATH=$(readlink -f "$APP_ROOT/backups/full/latest")

DB_NAME=mall_distribution
DB_USER=mall_user
DB_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
[[ -n "$DB_PASSWORD" ]]
MYSQL=(mysql -h127.0.0.1 -u"$DB_USER" "$DB_NAME")
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$RELEASE_DIR/20260808_add_product_safety_stock.sql"
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$RELEASE_DIR/20260808_add_shop_service_addresses.sql"
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$RELEASE_DIR/20260808_add_after_sale_return_workflow.sql"

column_count=$(MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='$DB_NAME'
  AND ((TABLE_NAME='dms_shop_product' AND COLUMN_NAME='safety_stock')
    OR (TABLE_NAME='dms_shop_sku' AND COLUMN_NAME='safety_stock')
    OR (TABLE_NAME='dms_shop_product' AND COLUMN_NAME IN ('shipping_address_id','return_address_id'))
    OR (TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME IN ('return_address_id','return_address'))
    OR (TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME IN ('return_delivery_company','return_delivery_no','return_shipped_at','return_received_at')));")
[[ "$column_count" == "10" ]]

rm -rf -- "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
install -d -m 0755 "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"
MUTATED=1

systemctl stop lingqimall-distribution.service
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
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
nginx -t
systemctl reload nginx

admin_html=$(curl -fsS --max-time 12 -H 'Cache-Control: no-cache' 'https://lingqimall.com/admin/?release=1.0.31')
shop_html=$(curl -fsS --max-time 12 -H 'Cache-Control: no-cache' 'https://lingqimall.com/?release=1.0.31')
grep -q '<div id="app">' <<< "$admin_html"
grep -q '<div id="app">' <<< "$shop_html"
admin_entry=$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)
shop_entry=$(grep -o '/assets/index-[^" ]*\.js' <<< "$shop_html" | head -1)
curl -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com$admin_entry?release=1.0.31" >/dev/null
curl -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com$shop_entry?release=1.0.31" >/dev/null
grep -R -Fq '确认收货并退款' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '提交退货物流' "$APP_ROOT/nginx/shop/assets"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.31" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=1.0.31 backup=$BACKUP_PATH admin_entry=$admin_entry shop_entry=$shop_entry"
