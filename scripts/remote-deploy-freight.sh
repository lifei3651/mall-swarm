#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
DB_USER=mall_user
EXPECTED_JAR_SHA256=413bd1b095f0dff0e647bc6bc27295476d5f602302f3c08d79c486f174cbe449
EXPECTED_ADMIN_SHA256=45665fee6e966ba436fe9f4ae9f429145997b670cf5fbd49d13fe5d0aad59607
EXPECTED_SHOP_SHA256=756da591cecfd5b724a66c8c3f36900a9f7c36864128d3a7f5ecc3c63790df9e
EXPECTED_MIGRATION_SHA256=bd497761e837776c8112fb90e6c44216ced3f5197d018aa5db0334c74910a054
MIGRATION_SQL=/tmp/20260715_freight_templates.sql
DB_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL=(mysql -h127.0.0.1 -u"$DB_USER" "$DB_NAME")

echo "$EXPECTED_JAR_SHA256  /tmp/lingqimall-compatible.jar" | sha256sum -c -
echo "$EXPECTED_ADMIN_SHA256  /tmp/lingqimall-admin-dist.tar.gz" | sha256sum -c -
echo "$EXPECTED_SHOP_SHA256  /tmp/lingqimall-shop-dist.tar.gz" | sha256sum -c -
echo "$EXPECTED_MIGRATION_SHA256  $MIGRATION_SQL" | sha256sum -c -

column_exists() {
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$1' AND COLUMN_NAME='$2'"
}

table_exists() {
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$1'"
}

if [[ "$(table_exists dms_freight_template)" == "0" \
   && "$(column_exists dms_shop_product freight_template_id)" == "0" \
   && "$(column_exists dms_shop_order receiver_province)" == "0" ]]; then
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$MIGRATION_SQL"
fi

if [[ "$(table_exists dms_freight_template)" != "1" \
   || "$(column_exists dms_shop_product delivery_province)" != "1" \
   || "$(column_exists dms_shop_product delivery_city)" != "1" \
   || "$(column_exists dms_shop_product delivery_district)" != "1" \
   || "$(column_exists dms_shop_product freight_template_id)" != "1" \
   || "$(column_exists dms_shop_order receiver_province)" != "1" \
   || "$(column_exists dms_shop_order receiver_city)" != "1" \
   || "$(column_exists dms_shop_order receiver_district)" != "1" \
   || "$(column_exists dms_shop_order receiver_detail_address)" != "1" ]]; then
  echo '运费模板数据库结构不完整，终止发布' >&2
  exit 1
fi

systemctl stop lingqimall-distribution.service
install -m 0644 /tmp/lingqimall-compatible.jar "$APP_ROOT/app/mall-distribution.jar"

# 生产外部配置完全覆盖包内配置，因此用 systemd 环境变量固定上传上限。
install -d -m 0755 /etc/systemd/system/lingqimall-distribution.service.d
printf '%s\n' \
  '[Service]' \
  'Environment="SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=5MB"' \
  'Environment="SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=6MB"' \
  > /etc/systemd/system/lingqimall-distribution.service.d/upload-limit.conf
systemctl daemon-reload
systemctl start lingqimall-distribution.service

for _ in $(seq 1 45); do
  if curl -fsS http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done
curl -fsS http://127.0.0.1:8086/actuator/health

rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
tar -xzf /tmp/lingqimall-admin-dist.tar.gz -C "$APP_ROOT/nginx/admin"
tar -xzf /tmp/lingqimall-shop-dist.tar.gz -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"
nginx -t
systemctl reload nginx

# 删除本次旧版分步保存失败留下的 4 条无 SKU、无订单测试商品。
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -e "
DELETE product FROM dms_shop_product product
WHERE product.id IN (5,6,7,8)
  AND LOWER(product.product_name)='test'
  AND NOT EXISTS (SELECT 1 FROM dms_shop_sku sku WHERE sku.product_id=product.id)
  AND NOT EXISTS (SELECT 1 FROM dms_shop_order_item item WHERE item.product_id=product.id);"

sha256sum \
  "$APP_ROOT/app/mall-distribution.jar" \
  "$APP_ROOT/nginx/admin/index.html" \
  "$APP_ROOT/nginx/shop/index.html"

MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "
SELECT CONCAT('freight_template_table=', COUNT(*)) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_freight_template';
SELECT CONCAT('product_freight_columns=', COUNT(*)) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_shop_product'
 AND COLUMN_NAME IN ('delivery_province','delivery_city','delivery_district','freight_template_id');
SELECT CONCAT('order_address_columns=', COUNT(*)) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_shop_order'
 AND COLUMN_NAME IN ('receiver_province','receiver_city','receiver_district','receiver_detail_address');
SELECT CONCAT('failed_test_products=', COUNT(*)) FROM dms_shop_product WHERE id IN (5,6,7,8);"
echo deployment-complete
