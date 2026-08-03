#!/usr/bin/env bash
set -Eeuo pipefail

# 使用方式：上传发布产物和 SQL 到 /opt/lingqimall/upload 后，以 root 执行。
# 该脚本会备份旧环境，然后重建 mall_distribution；只用于确认不保留旧测试数据的首次正式部署。
APP_ROOT=/opt/lingqimall
UPLOAD_DIR="$APP_ROOT/upload"
BACKUP_DIR="$APP_ROOT/backups/reset-$(date +%Y%m%d%H%M%S)"
DATABASE=mall_distribution

mkdir -p "$BACKUP_DIR" "$APP_ROOT/releases"
systemctl stop lingqimall-distribution

mysqldump --single-transaction --routines --events "$DATABASE" > "$BACKUP_DIR/${DATABASE}.sql"
test -s "$BACKUP_DIR/${DATABASE}.sql"
cp -a "$APP_ROOT/app/mall-distribution.jar" "$BACKUP_DIR/"
cp -a /etc/nginx/sites-available/lingqimall.conf "$BACKUP_DIR/lingqimall.conf"
cp -a "$APP_ROOT/nginx/shop" "$BACKUP_DIR/shop" 2>/dev/null || true
cp -a "$APP_ROOT/nginx/admin" "$BACKUP_DIR/admin" 2>/dev/null || true

mysql -e "DROP DATABASE IF EXISTS \`$DATABASE\`; CREATE DATABASE \`$DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql "$DATABASE" < "$UPLOAD_DIR/distribution.sql"
for migration in \
  20260711_commission_settlement_batch_upgrade.sql \
  20260711_line_change_approval_upgrade.sql \
  20260711_order_relation_snapshot_upgrade.sql \
  20260714_erp_integration_upgrade.sql \
  20260714_new_retail_default_rule.sql; do
  mysql "$DATABASE" < "$UPLOAD_DIR/$migration"
done

mysql -Nse "SELECT version_no, status FROM $DATABASE.dms_commission_rule_version WHERE tenant_id=1" \
  | grep -qx 'NEW_RETAIL_SIMPLE_DEFAULT[[:space:]]1'

RELEASE_DIR="$APP_ROOT/releases/$(date +%Y%m%d%H%M%S)"
mkdir -p "$RELEASE_DIR"
install -m 0644 "$UPLOAD_DIR/mall-distribution-1.0-SNAPSHOT.jar" "$APP_ROOT/app/mall-distribution.jar"
tar -xzf "$UPLOAD_DIR/lingqimall-shop-20260714.tgz" -C "$RELEASE_DIR"
mv "$RELEASE_DIR/dist" "$RELEASE_DIR/shop"
tar -xzf "$UPLOAD_DIR/lingqimall-admin-20260714.tgz" -C "$RELEASE_DIR"
mv "$RELEASE_DIR/dist" "$RELEASE_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$BACKUP_DIR/shop-live" 2>/dev/null || true
mv "$APP_ROOT/nginx/admin" "$BACKUP_DIR/admin-live" 2>/dev/null || true
mv "$RELEASE_DIR/shop" "$APP_ROOT/nginx/shop"
mv "$RELEASE_DIR/admin" "$APP_ROOT/nginx/admin"

cp "$APP_ROOT/backups/lingqimall-nginx-before-icp-20260708093939.conf" /etc/nginx/sites-available/lingqimall.conf
nginx -t
systemctl reload nginx
systemctl start lingqimall-distribution
systemctl --no-pager --full status lingqimall-distribution
