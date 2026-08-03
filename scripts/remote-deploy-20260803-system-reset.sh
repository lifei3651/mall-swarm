#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260803-111118
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260803-111118

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"
MIGRATION="$RELEASE_DIR/20260803_system_fund_accounts.sql"
CLEANUP_SQL="$RELEASE_DIR/20260803_clean_test_business_data.sql"

EXPECTED_JAR=0a6b608f4e40a9e4cd9cd9e227fb0b0c196d14eaa3b71a53f890f338d34eb1a5
EXPECTED_ADMIN=83e38f95512ebe619a5de4105d89149ba768d7302f853bd9c4b9edda2c2bb76b
EXPECTED_SHOP=95ea641d42a6b7b4b300c9892b06c988a67c72fd7580fb1a3242d55dcc7750ec
EXPECTED_MIGRATION=0a6dcf2374e18e147482a962f20ceaac071b6451e98a75367702314f350ed77f
EXPECTED_CLEANUP=bfd89d4d91dd1897e02c62adf9f462bef18a8d839b81525bbfa2c2783fea2190

MUTATED=0

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

restore_database() {
  local db_pass
  db_pass=$(database_password)
  MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user mall_distribution \
    < <(gzip -dc "$ROLLBACK_DIR/database.sql.gz")
  unset db_pass
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "release failed; restoring database, application and web files" >&2
    systemctl stop lingqimall-distribution.service || true
    restore_database || true
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
echo "$EXPECTED_MIGRATION  $MIGRATION" | sha256sum -c -
echo "$EXPECTED_CLEANUP  $CLEANUP_SQL" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

db_pass=$(database_password)
before_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
  "SELECT CONCAT((SELECT COUNT(*) FROM dms_shop_member), ':', (SELECT COUNT(*) FROM dms_shop_order), ':', (SELECT COUNT(*) FROM dms_member_asset_flow))" \
  mall_distribution)
unset db_pass
echo "validation-stage=preflight core-counts=$before_counts"

# 双重备份：保留标准全量备份，同时建立本次自动回滚专用副本。
/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
db_pass=$(database_password)
MYSQL_PWD="$db_pass" mysqldump --ssl-mode=REQUIRED -h127.0.0.1 -umall_user \
  --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces \
  mall_distribution | gzip -9 > "$ROLLBACK_DIR/database.sql.gz"
unset db_pass
gzip -t "$ROLLBACK_DIR/database.sql.gz"
(cd "$ROLLBACK_DIR" && sha256sum database.sql.gz mall-distribution.jar admin.tar.gz shop.tar.gz > SHA256SUMS)

MUTATED=1
systemctl stop lingqimall-distribution.service

db_pass=$(database_password)
MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user mall_distribution < "$MIGRATION"
MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user mall_distribution < "$CLEANUP_SQL"
unset db_pass

install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
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

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
grep -R -Fq '确认退出登录' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '让经营更清晰' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '实时数据统计技术' "$APP_ROOT/nginx/admin/assets"

db_pass=$(database_password)
post_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
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
  )" mall_distribution)
system_account_check=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
  "SELECT COUNT(*) FROM dms_shop_member member
   INNER JOIN dms_agent agent ON agent.user_id=member.user_id
   INNER JOIN dms_member_asset_account account ON account.agent_id=agent.id
   WHERE member.id IN (1,5) AND member.system_account=1 AND member.status=0
     AND agent.status=2 AND account.balance=0 AND account.total_in=0 AND account.total_out=0" \
  mall_distribution)
unset db_pass
IFS=: read -r customer_count internal_count order_count bonus_count flow_count \
  balance_total product_count category_count admin_count <<< "$post_counts"
[[ "$customer_count" == 0 ]]
[[ "$internal_count" == 2 ]]
[[ "$order_count" == 0 ]]
[[ "$bonus_count" == 0 ]]
[[ "$flow_count" == 0 ]]
[[ "$balance_total" =~ ^0([.]0+)?$ ]]
[[ "$product_count" -ge 1 ]]
[[ "$category_count" -ge 1 ]]
[[ "$admin_count" -ge 1 ]]
[[ "$system_account_check" == 2 ]]
echo "validation-stage=database-clean customer:internal:orders:bonus:flows:balance:products:categories:admins=$post_counts"

captcha_json=$(curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop')
home_json=$(curl -fsS --max-time 12 https://lingqimall.com/api/shop/home)
shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q 'data:image/png;base64,' <<< "$captcha_json"
grep -q '"code":200' <<< "$home_json"
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"

admin_auth_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/admin-auth/me')
wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/wallet/flows')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'http://lingqimall.com/')
[[ "$admin_auth_status" == 401 ]]
[[ "$wallet_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

security_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/)
admin_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/admin/)
grep -qi '^content-security-policy:' <<< "$security_headers"
grep -qi '^strict-transport-security:' <<< "$security_headers"
grep -qi '^cache-control:.*no-cache' <<< "$admin_headers"
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
echo "validation-stage=public-and-security-ok"

# 发布成功后清理临时回滚副本和上传包；标准全量备份按备份保留策略保存。
MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
