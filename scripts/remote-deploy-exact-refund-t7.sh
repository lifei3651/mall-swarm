#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
DB_USER=mall_user
MIGRATION_SQL=/tmp/20260716_exact_refund_and_t7_settlement.sql
EXPECTED_JAR_SHA256=475be6877fd3bcfbb496328ec160f7f3f74a70f8dce1667da25037cc2bddcce2
EXPECTED_ADMIN_SHA256=a23f317d8e984b3f41ecab6d902df8add89599bb1f2263a6794d59e4b8e72304
EXPECTED_SHOP_SHA256=94287e01e96fee39faa47c6a3125666ba82638ed6f9daeea0b2fec4fbbe090ae
EXPECTED_MIGRATION_SHA256=916998a960f0bb09efb7ffd5c301a48a447cdf465006be80541ba3aca4ad4b37
DB_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL=(mysql -h127.0.0.1 -u"$DB_USER" "$DB_NAME")
STAMP=$(date +%Y%m%d%H%M%S)
BACKUP_DIR="$APP_ROOT/backups/exact-refund-t7-$STAMP"
DEPLOYED=0

echo "$EXPECTED_JAR_SHA256  /tmp/lingqimall-compatible.jar" | sha256sum -c -
echo "$EXPECTED_ADMIN_SHA256  /tmp/lingqimall-admin-dist.tar.gz" | sha256sum -c -
echo "$EXPECTED_SHOP_SHA256  /tmp/lingqimall-shop-dist.tar.gz" | sha256sum -c -
echo "$EXPECTED_MIGRATION_SHA256  $MIGRATION_SQL" | sha256sum -c -

mkdir -p "$BACKUP_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$BACKUP_DIR/mall-distribution.jar"
tar -czf "$BACKUP_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$BACKUP_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
MYSQL_PWD="$DB_PASSWORD" mysqldump -h127.0.0.1 -u"$DB_USER" --single-transaction --no-tablespaces "$DB_NAME" \
  dms_finance_refund dms_shop_after_sale dms_commission_record dms_order_finance \
  dms_order_performance_detail dms_agent_account dms_commission_clawback > "$BACKUP_DIR/refund-settlement-tables.sql"

rollback() {
  if [[ "$DEPLOYED" == "1" ]]; then
    echo '发布失败，回滚后端和前端文件' >&2
    cp "$BACKUP_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
    rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
    tar -xzf "$BACKUP_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin"
    tar -xzf "$BACKUP_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop"
    systemctl restart lingqimall-distribution.service || true
    systemctl reload nginx || true
  fi
}
trap rollback ERR

systemctl stop lingqimall-distribution.service
DEPLOYED=1
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$MIGRATION_SQL"

column_count=$(MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='$DB_NAME'
  AND ((TABLE_NAME='dms_finance_refund' AND COLUMN_NAME IN ('product_refund_amount','freight_refund_amount','refund_quantity'))
    OR (TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME IN ('product_refund_amount','freight_refund_amount','refund_quantity')));")
table_count=$(MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_shop_after_sale_item';")
if [[ "$column_count" != "6" || "$table_count" != "1" ]]; then
  echo '售后退款数据库结构校验失败' >&2
  exit 1
fi

install -m 0644 /tmp/lingqimall-compatible.jar "$APP_ROOT/app/mall-distribution.jar"
systemctl start lingqimall-distribution.service
for _ in $(seq 1 45); do
  if curl -fsS http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done
curl -fsS http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
tar -xzf /tmp/lingqimall-admin-dist.tar.gz -C "$APP_ROOT/nginx/admin"
tar -xzf /tmp/lingqimall-shop-dist.tar.gz -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"
nginx -t
systemctl reload nginx

MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "
SELECT CONCAT('refund_columns=', COUNT(*)) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='$DB_NAME'
  AND ((TABLE_NAME='dms_finance_refund' AND COLUMN_NAME IN ('product_refund_amount','freight_refund_amount','refund_quantity'))
    OR (TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME IN ('product_refund_amount','freight_refund_amount','refund_quantity')));
SELECT CONCAT('after_sale_item_table=', COUNT(*)) FROM information_schema.TABLES
WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_shop_after_sale_item';
SELECT CONCAT('risk_rules=', COUNT(*)) FROM dms_finance_risk_rule
WHERE rule_code IN ('BONUS_PAYOUT_RATE_MAX','PROFIT_RATE_MIN','LOSS_ORDER_COUNT_MAX');"

sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/nginx/admin/index.html" "$APP_ROOT/nginx/shop/index.html"
DEPLOYED=0
trap - ERR
echo "deployment-complete backup=$BACKUP_DIR"
