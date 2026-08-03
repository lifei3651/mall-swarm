#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
DB_USER=mall_user
EXPECTED_JAR_SHA256=cc0568f2471cd294b0037d930077d2a914572d78fd13b094b873a981b37db76a
EXPECTED_RULE_SHA256=2c35da69a23cc21d295d5ac3734de2d8f750353e27b704defca82b9fd8205521
EXPECTED_LEGACY_REMOVAL_SHA256=89594364d437ebecbdde4e3a95224b0d7dab2f8eb795d93c6e85beac4e3dbcce
RULE_SQL=/tmp/20260714_new_retail_default_rule.sql
LEGACY_REMOVAL_SQL=/tmp/20260715_remove_legacy_bonus.sql
DB_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL=(mysql -h127.0.0.1 -u"$DB_USER" "$DB_NAME")

echo "$EXPECTED_JAR_SHA256  /tmp/lingqimall-compatible.jar" | sha256sum -c -
echo "$EXPECTED_RULE_SHA256  $RULE_SQL" | sha256sum -c -
echo "$EXPECTED_LEGACY_REMOVAL_SHA256  $LEGACY_REMOVAL_SQL" | sha256sum -c -

column_exists() {
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$1' AND COLUMN_NAME='$2'"
}

table_exists() {
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$1'"
}

MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -e "
CREATE TABLE IF NOT EXISTS dms_migration_baseline (
  id bigint NOT NULL AUTO_INCREMENT,
  batch_no varchar(64) NOT NULL,
  agent_id bigint NOT NULL,
  user_id bigint NOT NULL,
  external_member_code varchar(128) NOT NULL,
  historical_order_count int NOT NULL DEFAULT 0,
  historical_personal_performance decimal(14,2) NOT NULL DEFAULT 0,
  historical_team_performance decimal(14,2) NOT NULL DEFAULT 0,
  initial_level tinyint NOT NULL DEFAULT 1,
  cutover_time datetime NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_migration_agent (agent_id),
  UNIQUE KEY uk_migration_external_code (external_member_code),
  KEY idx_migration_batch (batch_no),
  KEY idx_migration_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部团队平移期初基线'"

# 从这里开始修改奖金及业绩结构，避免旧程序在迁移窗口内继续写入。
systemctl stop lingqimall-distribution.service

# 同步唯一启用的新零售规则说明，并确保旧三级/其他版本不会被选中。
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$RULE_SQL"

# 旧程序没有持续维护 total_orders；按有效业绩明细商品件数 + 外部迁移期初件数幂等校正。
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -e "
UPDATE dms_agent_account account
LEFT JOIN (
  SELECT target_agent_id, COALESCE(SUM(quantity), 0) AS effective_units
  FROM dms_order_performance_detail
  WHERE status = 1
  GROUP BY target_agent_id
) performance ON performance.target_agent_id = account.agent_id
LEFT JOIN dms_migration_baseline baseline ON baseline.agent_id = account.agent_id
SET account.total_orders = GREATEST(
  COALESCE(performance.effective_units, 0) + COALESCE(baseline.historical_order_count, 0), 0
);
UPDATE dms_shop_notice
SET title = '内部测试商城已开启',
    content = '当前为内部全流程测试环境，正式支付通道完成商户配置后启用；生产环境不开放模拟支付。'
WHERE id = 1 AND content LIKE '%模拟支付%'"

# 只在旧结构存在时执行一次：补新版字段、改为不限层深度、删除旧表。
if [[ "$(column_exists dms_commission_record bonus_type)" == "0" ]]; then
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$LEGACY_REMOVAL_SQL"
fi

if [[ "$(column_exists dms_commission_record bonus_type)" != "1" \
   || "$(column_exists dms_commission_record rule_id)" != "0" \
   || "$(table_exists dms_commission_rule)" != "0" \
   || "$(table_exists dms_bonus_rule_node)" != "0" ]]; then
  echo '旧版奖金结构未完全移除，终止发布' >&2
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
curl -fsS http://127.0.0.1:8086/actuator/health

# 新程序确认健康后再删除旧商品卡级字段，避免旧程序在切换窗口读取失败。
if [[ "$(column_exists dms_shop_product distribution_enabled)" != "0" ]]; then
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -e "ALTER TABLE dms_shop_product DROP COLUMN distribution_enabled"
fi
if [[ "$(column_exists dms_shop_product activation_level)" != "0" ]]; then
  MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -e "ALTER TABLE dms_shop_product DROP COLUMN activation_level"
fi

rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
tar -xzf /tmp/lingqimall-admin-dist.tar.gz -C "$APP_ROOT/nginx/admin"
tar -xzf /tmp/lingqimall-shop-dist.tar.gz -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"

sha256sum \
  "$APP_ROOT/app/mall-distribution.jar" \
  "$APP_ROOT/nginx/admin/index.html" \
  "$APP_ROOT/nginx/shop/index.html"

MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "
SELECT CONCAT('removed_product_rank_columns=', 2 - COUNT(*)) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_shop_product'
 AND COLUMN_NAME IN ('distribution_enabled','activation_level');
SELECT CONCAT('baseline_table=', COUNT(*)) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_migration_baseline';
SELECT CONCAT('active_bonus_version=', version_no) FROM dms_commission_rule_version WHERE tenant_id=1 AND status=1;
SELECT CONCAT('active_bonus_version_count=', COUNT(*)) FROM dms_commission_rule_version
 WHERE tenant_id=1 AND status=1 AND version_no='NEW_RETAIL_SIMPLE_DEFAULT';
SELECT CONCAT('legacy_bonus_tables=', COUNT(*)) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME IN ('dms_commission_rule','dms_bonus_rule_node');
SELECT CONCAT('legacy_record_columns=', COUNT(*)) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_commission_record' AND COLUMN_NAME='rule_id';
SELECT CONCAT('new_record_columns=', COUNT(*)) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_commission_record'
 AND COLUMN_NAME IN ('tenant_id','rule_version_id','bonus_type');
SELECT CONCAT('account_units_mismatch=', COUNT(*)) FROM dms_agent_account account
LEFT JOIN (
  SELECT target_agent_id, COALESCE(SUM(quantity),0) AS effective_units
  FROM dms_order_performance_detail WHERE status=1 GROUP BY target_agent_id
) performance ON performance.target_agent_id=account.agent_id
LEFT JOIN dms_migration_baseline baseline ON baseline.agent_id=account.agent_id
WHERE account.total_orders != GREATEST(COALESCE(performance.effective_units,0)+COALESCE(baseline.historical_order_count,0),0);"
echo deployment-complete
