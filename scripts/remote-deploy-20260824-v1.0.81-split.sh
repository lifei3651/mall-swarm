#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.81
EXPECTED_VERSION=1.0.81
EXPECTED_PREVIOUS_VERSION=1.0.80
EXPECTED_GIT_COMMIT=3ce3e11fa679786723be1054fafc0906248a198b
EXPECTED_BUILD_ID=20260824-1110-1.0.81
EXPECTED_JAR_SHA256=6cf2b116750948be0f1c06d4b3c19f721406cd3e9eb51587c34a360ddcde2d75
EXPECTED_PREVIOUS_MIGRATIONS=21
EXPECTED_MIGRATIONS=21
DB_NAME=mall_distribution
ROLLBACK_DIR=""
NEW_ADMIN=""
NEW_SHOP=""
NEW_TEAM=""
BACKUP_BEFORE=""
BACKUP_AFTER=""
BEFORE_COUNTS=""
ENCRYPTION_ENV_SHA=""
ENCRYPTION_DROPIN_SHA=""
APP_MUTATED=0
STATIC_MUTATED=0
NGINX_MUTATED=0
BACKUP_TOOL_MUTATED=0

mysql_cmd() { mysql --protocol=socket -uroot "$DB_NAME" "$@"; }

database_counts() {
  mysql_cmd -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=0), ':',
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=1), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_shop_product), ':',
    (SELECT COUNT(*) FROM dms_shop_category), ':',
    (SELECT COUNT(*) FROM dms_admin_user WHERE status=1)
  )"
}

sensitive_value_count() {
  mysql_cmd -NBe "SELECT
      (SELECT COUNT(*) FROM dms_agent WHERE id_card IS NOT NULL AND id_card <> '') +
      (SELECT COUNT(*) FROM dms_agent WHERE bank_account IS NOT NULL AND bank_account <> '') +
      (SELECT COUNT(*) FROM dms_erp_integration WHERE app_secret IS NOT NULL AND app_secret <> '') +
      (SELECT COUNT(*) FROM dms_erp_integration WHERE callback_token IS NOT NULL AND callback_token <> '') +
      (SELECT COUNT(*) FROM dms_withdraw_record WHERE bank_account IS NOT NULL AND bank_account <> '') +
      (SELECT COUNT(*) FROM dms_merchant WHERE bank_account_no IS NOT NULL AND bank_account_no <> '') +
      (SELECT COUNT(*) FROM dms_merchant_withdrawal WHERE bank_account_no_snapshot IS NOT NULL AND bank_account_no_snapshot <> '') +
      (SELECT COUNT(*) FROM dms_import_detail WHERE raw_data IS NOT NULL AND raw_data <> '')"
}

plaintext_sensitive_value_count() {
  mysql_cmd -NBe "SELECT
      (SELECT COUNT(*) FROM dms_agent WHERE id_card IS NOT NULL AND id_card <> '' AND id_card NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_agent WHERE bank_account IS NOT NULL AND bank_account <> '' AND bank_account NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_erp_integration WHERE app_secret IS NOT NULL AND app_secret <> '' AND app_secret NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_erp_integration WHERE callback_token IS NOT NULL AND callback_token <> '' AND callback_token NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_withdraw_record WHERE bank_account IS NOT NULL AND bank_account <> '' AND bank_account NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_merchant WHERE bank_account_no IS NOT NULL AND bank_account_no <> '' AND bank_account_no NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_merchant_withdrawal WHERE bank_account_no_snapshot IS NOT NULL AND bank_account_no_snapshot <> '' AND bank_account_no_snapshot NOT LIKE 'enc:v1:%') +
      (SELECT COUNT(*) FROM dms_import_detail WHERE raw_data IS NOT NULL AND raw_data <> '' AND raw_data NOT LIKE 'enc:v1:%')"
}

wait_for_health() {
  local healthy=0
  for _ in $(seq 1 60); do
    if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      healthy=1
      break
    fi
    sleep 2
  done
  [[ "$healthy" == 1 ]]
}

verify_backup() {
  local backup_path=$1
  [[ "$backup_path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]]
  (
    cd "$backup_path"
    sha256sum -c SHA256SUMS
    gzip -t database.sql.gz
    local listing
    listing="$(mktemp)"
    trap 'rm -f "$listing"' EXIT
    tar -tzf files-and-config.tar.gz > "$listing"
    grep -Fx 'opt/lingqimall/app/mall-distribution.jar' "$listing" >/dev/null
    grep -Fx 'opt/lingqimall/config/application.yml' "$listing" >/dev/null
    grep -Fx 'opt/lingqimall/nginx/admin/index.html' "$listing" >/dev/null
    grep -Fx 'opt/lingqimall/nginx/shop/index.html' "$listing" >/dev/null
    grep -Fx 'opt/lingqimall/nginx/team/index.html' "$listing" >/dev/null
    grep -Fx 'etc/systemd/system/lingqimall-distribution.service' "$listing" >/dev/null
    grep -Fx 'etc/nginx/conf.d/lingqimall.conf' "$listing" >/dev/null
    grep -Fx 'opt/lingqimall/config/data-encryption.env' "$listing" >/dev/null
    grep -Fx 'etc/systemd/system/lingqimall-distribution.service.d/data-encryption.conf' "$listing" >/dev/null
  )
}

cleanup_staging() {
  [[ -n "$NEW_ADMIN" && -d "$NEW_ADMIN" ]] && rm -rf -- "$NEW_ADMIN"
  [[ -n "$NEW_SHOP" && -d "$NEW_SHOP" ]] && rm -rf -- "$NEW_SHOP"
  [[ -n "$NEW_TEAM" && -d "$NEW_TEAM" ]] && rm -rf -- "$NEW_TEAM"
}

rollback() {
  local code=$?
  if [[ "$code" != 0 ]]; then
    echo "1.0.81 release failed; entering bounded recovery" >&2
    if [[ "$STATIC_MUTATED" == 1 ]]; then
      for site in admin shop team; do
        if [[ -d "$ROLLBACK_DIR/$site" ]]; then
          rm -rf -- "$APP_ROOT/nginx/$site"
          mv "$ROLLBACK_DIR/$site" "$APP_ROOT/nginx/$site"
        fi
      done
      chown -R nginx:nginx "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" 2>/dev/null || true
    fi
    if [[ "$NGINX_MUTATED" == 1 ]]; then
      [[ -s "$ROLLBACK_DIR/lingqimall.conf" ]] && install -m 0644 "$ROLLBACK_DIR/lingqimall.conf" /etc/nginx/conf.d/lingqimall.conf || true
      [[ -s "$ROLLBACK_DIR/00-lingqimall-limits.conf" ]] && install -m 0644 "$ROLLBACK_DIR/00-lingqimall-limits.conf" /etc/nginx/conf.d/00-lingqimall-limits.conf || true
      nginx -t && systemctl reload nginx || true
    fi
    if [[ "$APP_MUTATED" == 1 ]]; then
      systemctl stop lingqimall-distribution.service || true
      [[ -s "$ROLLBACK_DIR/mall-distribution.jar" ]] && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
      [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
      systemctl start lingqimall-distribution.service || true
    fi
    if [[ "$BACKUP_TOOL_MUTATED" == 1 && -s "$ROLLBACK_DIR/lingqimall-backup" ]]; then
      install -m 0750 "$ROLLBACK_DIR/lingqimall-backup" /usr/local/sbin/lingqimall-backup || true
    fi
  fi
  cleanup_staging
  exit "$code"
}
trap rollback EXIT

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.81 ]]
for file in mall-distribution.jar admin.tar.gz shop.tar.gz team.tar.gz integrated.tar.gz VERSION SHA256SUMS production-backup.sh db-migrate.sh lingqimall.conf lingqimall-security.conf; do
  [[ -s "$RELEASE_DIR/$file" ]]
done
[[ -x "$RELEASE_DIR/production-backup.sh" && -x "$RELEASE_DIR/db-migrate.sh" ]]
[[ "$(find "$RELEASE_DIR/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | wc -l)" == "$EXPECTED_MIGRATIONS" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
[[ "$(sha256sum "$RELEASE_DIR/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
tar -tzf "$RELEASE_DIR/integrated.tar.gz" | grep -Fx './version.json' >/dev/null

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
BEFORE_COUNTS="$(database_counts)"
ENCRYPTION_ENV_SHA="$(sha256sum "$APP_ROOT/config/data-encryption.env" | awk '{print $1}')"
ENCRYPTION_DROPIN_SHA="$(sha256sum /etc/systemd/system/lingqimall-distribution.service.d/data-encryption.conf | awk '{print $1}')"
[[ "$(plaintext_sensitive_value_count)" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_PREVIOUS_MIGRATIONS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE')" == 0 ]]

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.81.XXXXXX)"
install -m 0600 /usr/local/sbin/lingqimall-backup "$ROLLBACK_DIR/lingqimall-backup"
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
install -m 0600 /etc/nginx/conf.d/lingqimall.conf "$ROLLBACK_DIR/lingqimall.conf"
install -m 0600 /etc/nginx/conf.d/00-lingqimall-limits.conf "$ROLLBACK_DIR/00-lingqimall-limits.conf"
install -m 0750 "$RELEASE_DIR/production-backup.sh" /usr/local/sbin/lingqimall-backup
BACKUP_TOOL_MUTATED=1

DB_AUTH_MODE=socket DB_USER=root /usr/local/sbin/lingqimall-backup
BACKUP_BEFORE="$(readlink -f "$APP_ROOT/backups/full/latest")"
verify_backup "$BACKUP_BEFORE"
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.81.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.81.XXXXXX")"
NEW_TEAM="$(mktemp -d "$APP_ROOT/nginx/.team-v1.0.81.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
tar -xzf "$RELEASE_DIR/team.tar.gz" -C "$NEW_TEAM"
for manifest_spec in "$NEW_ADMIN/version.json:admin" "$NEW_SHOP/version.json:storefront-public" "$NEW_TEAM/version.json:team-h5"; do
  manifest="${manifest_spec%%:*}"
  application="${manifest_spec##*:}"
  grep -q '"version": "1.0.81"' "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$manifest"
  grep -q "\"application\": \"$application\"" "$manifest"
done
if find "$NEW_ADMIN" "$NEW_SHOP" "$NEW_TEAM" -type f -name '*.map' -print -quit | grep -q .; then
  echo "source map found in release assets" >&2
  exit 1
fi
chown -R nginx:nginx "$NEW_ADMIN" "$NEW_SHOP" "$NEW_TEAM"

systemctl stop lingqimall-distribution.service
APP_MUTATED=1
MIGRATION_ROOT_DIR="$RELEASE_DIR" DB_AUTH_MODE=socket DB_USER=root DB_NAME="$DB_NAME" "$RELEASE_DIR/db-migrate.sh" apply
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_MIGRATIONS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]

install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start lingqimall-distribution.service
wait_for_health
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
[[ "$(plaintext_sensitive_value_count)" == 0 ]]
curl -fsS --max-time 12 http://127.0.0.1:8086/shop/home >/dev/null
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
[[ "$(sha256sum "$APP_ROOT/config/data-encryption.env" | awk '{print $1}')" == "$ENCRYPTION_ENV_SHA" ]]
[[ "$(sha256sum /etc/systemd/system/lingqimall-distribution.service.d/data-encryption.conf | awk '{print $1}')" == "$ENCRYPTION_DROPIN_SHA" ]]

mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
mv "$APP_ROOT/nginx/team" "$ROLLBACK_DIR/team"
mv "$NEW_ADMIN" "$APP_ROOT/nginx/admin"; NEW_ADMIN=""
mv "$NEW_SHOP" "$APP_ROOT/nginx/shop"; NEW_SHOP=""
mv "$NEW_TEAM" "$APP_ROOT/nginx/team"; NEW_TEAM=""
STATIC_MUTATED=1
install -m 0644 "$RELEASE_DIR/lingqimall.conf" /etc/nginx/conf.d/lingqimall.conf
install -m 0644 "$RELEASE_DIR/lingqimall-security.conf" /etc/nginx/conf.d/00-lingqimall-limits.conf
NGINX_MUTATED=1
nginx -t
systemctl reload nginx

public_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?release=$EXPECTED_BUILD_ID")"
team_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://www.lingqimall.com/version.json?release=$EXPECTED_BUILD_ID")"
admin_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/version.json?release=$EXPECTED_BUILD_ID")"
for manifest_spec in "$public_manifest|storefront-public" "$team_manifest|team-h5" "$admin_manifest|admin"; do
  manifest="${manifest_spec%|*}"
  application="${manifest_spec##*|}"
  grep -q '"version": "1.0.81"' <<< "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" <<< "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" <<< "$manifest"
  grep -q "\"application\": \"$application\"" <<< "$manifest"
done

for url in 'https://lingqimall.com/' 'https://www.lingqimall.com/' 'https://lingqimall.com/admin/'; do
  curl --http1.1 -fsS --max-time 12 "$url" | grep -q '<div id="app">'
done
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/home' >/dev/null
curl --http1.1 -fsS --max-time 12 'https://www.lingqimall.com/api/shop/home' >/dev/null
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/live-rooms?limit=1' | grep -q '"code":200'
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/new-arrivals?limit=1' | grep -q '"code":200'
# 使用非法手机号只验证新路由存在和服务端校验生效，不触发真实短信发送。
sms_login_probe="$(curl --http1.1 -sS --max-time 12 -H 'Content-Type: application/json' \
  --data '{"phone":"1"}' 'https://lingqimall.com/api/sms/send/login')"
grep -q '请输入正确的11位手机号' <<< "$sms_login_probe"
product_response="$(curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/products?pageNum=1&pageSize=1')"
for forbidden in costPrice costAmount bvValue safetyStock shippingAddress shippingAddressId deliveryAddress returnAddressId freightTemplateId repurchasePrice repurchasePv repurchaseEnabled repurchaseConfig settlementPrice merchantId; do
  ! grep -q "\"$forbidden\"" <<< "$product_response"
done
for host in lingqimall.com www.lingqimall.com; do
  [[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://$host/api/distribution/admin-auth/me")" == 401 ]]
  [[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://$host/api/actuator/health")" == 404 ]]
done
for path in '/.env' '/.git/config' '/phpmyadmin/' '/api/swagger-ui/index.html'; do
  [[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://lingqimall.com$path")" == 404 ]]
done
security_headers="$(curl --http1.1 -fsSI --max-time 12 https://lingqimall.com/)"
grep -qi '^content-security-policy:' <<< "$security_headers"
grep -qi '^permissions-policy:' <<< "$security_headers"
grep -qi '^strict-transport-security:' <<< "$security_headers"

DB_AUTH_MODE=socket DB_USER=root /usr/local/sbin/lingqimall-backup
BACKUP_AFTER="$(readlink -f "$APP_ROOT/backups/full/latest")"
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
verify_backup "$BACKUP_AFTER"
tar -tzf "$BACKUP_AFTER/files-and-config.tar.gz" | grep -Fx 'opt/lingqimall/config/data-encryption.env' >/dev/null
tar -tzf "$BACKUP_AFTER/files-and-config.tar.gz" | grep -Fx 'etc/systemd/system/lingqimall-distribution.service.d/data-encryption.conf' >/dev/null

[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
[[ "$(plaintext_sensitive_value_count)" == 0 ]]
[[ "$(sha256sum "$APP_ROOT/config/data-encryption.env" | awk '{print $1}')" == "$ENCRYPTION_ENV_SHA" ]]
[[ "$(sha256sum /etc/systemd/system/lingqimall-distribution.service.d/data-encryption.conf | awk '{print $1}')" == "$ENCRYPTION_DROPIN_SHA" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_MIGRATIONS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG

sleep 16
if journalctl -u lingqimall-distribution.service --since '-3 minutes' --no-pager | grep -E 'Application run failed|OutOfMemoryError|Access denied for user|UnsatisfiedDependencyException|BeanCreationException| ERROR ' >/dev/null; then
  echo "1.0.81 startup log verification failed" >&2
  exit 1
fi

APP_MUTATED=0
STATIC_MUTATED=0
NGINX_MUTATED=0
BACKUP_TOOL_MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER build=$EXPECTED_BUILD_ID core-counts=$BEFORE_COUNTS migrations=$EXPECTED_MIGRATIONS encryption-preserved=yes jar=$EXPECTED_JAR_SHA256"
