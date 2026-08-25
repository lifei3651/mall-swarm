#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.88
EXPECTED_VERSION=1.0.88
EXPECTED_PREVIOUS_VERSION=1.0.87
EXPECTED_PREVIOUS_GIT_COMMIT=7f66a83cdc10b44734c1d5788fbc8371758728d3
EXPECTED_PREVIOUS_BUILD_ID=20260825-0950-1.0.87
EXPECTED_GIT_COMMIT=85c7a9ed268f4603a1675456c631ea737f4c61bf
EXPECTED_BUILD_ID=20260825-1357-1.0.88
EXPECTED_JAR_SHA256=5661a66793d5818e7440fcf8e32ea2495fa5228b8f5b776aaf41934b38f5c4db
EXPECTED_PREVIOUS_MIGRATIONS=26
EXPECTED_MIGRATIONS=26

# 默认只允许验签和阅读。正式执行必须同时提供参数与版本绑定授权变量，避免误触生产变更。
if [[ "${1:-}" != "--authorize-release" || "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" != "$EXPECTED_VERSION" ]]; then
  echo "waiting-for-explicit-release-authorization version=$EXPECTED_VERSION" >&2
  echo "authorized usage: LINGQIMALL_RELEASE_AUTHORIZATION=$EXPECTED_VERSION ./release.sh --authorize-release" >&2
  exit 3
fi
shift
[[ "$#" == 0 ]]

DB_NAME=mall_distribution
ROLLBACK_DIR=""
NEW_ADMIN=""
NEW_SHOP=""
NEW_TEAM=""
BACKUP_BEFORE=""
BACKUP_AFTER=""
BEFORE_COUNTS=""
DB_CREATE_SQL=""
ENCRYPTION_ENV_SHA=""
ENCRYPTION_DROPIN_SHA=""
APP_MUTATED=0
STATIC_MUTATED=0
NGINX_MUTATED=0
BACKUP_TOOL_MUTATED=0
DB_MUTATED=0
MIGRATION_COMPLETED=0

mysql_cmd() { mysql --protocol=socket -uroot "$DB_NAME" "$@"; }
mysql_root() { mysql --protocol=socket -uroot "$@"; }

database_counts_for() {
  local database=$1
  mysql --protocol=socket -uroot "$database" -NBe "SELECT CONCAT(
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

database_counts() { database_counts_for "$DB_NAME"; }

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

message_table_count() {
  mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='${DB_NAME}' AND table_name IN
    ('dms_member_message','dms_message_template','dms_message_channel_config','dms_message_delivery_task')"
}

verify_message_schema() {
  [[ "$(message_table_count)" == 4 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_message_template')" == 15 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_message_channel_config')" == 15 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(DISTINCT category) FROM dms_message_template')" == 5 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COALESCE(SUM(sms_enabled),0)+COALESCE(SUM(app_push_enabled),0)+COALESCE(SUM(mini_program_enabled),0) FROM dms_message_channel_config')" == 0 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND index_name IN ('uk_member_message_event','idx_member_message_unread','idx_member_message_category','uk_message_template_event','uk_message_channel_event','uk_message_delivery_channel','idx_message_delivery_status')")" == 7 ]]
}

notification_kernel_table_count() {
  mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='${DB_NAME}' AND table_name IN
    ('dms_message_delivery_attempt','dms_message_cost_budget','dms_message_recipient_authorization','dms_message_delivery_receipt')"
}

verify_notification_kernel() {
  [[ "$(notification_kernel_table_count)" == 4 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='dms_message_delivery_task'")" == 25 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_message_cost_budget')" == 19 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COALESCE(SUM(enabled),0) FROM dms_message_cost_budget')" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COALESCE(SUM(daily_limit),0)+COALESCE(SUM(monthly_limit),0) FROM dms_message_cost_budget')" == 0.0000 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_message_recipient_authorization')" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_message_delivery_attempt')" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_message_delivery_receipt')" == 0 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_message_delivery_task WHERE channel IN ('SMS','APP_PUSH','MINI_PROGRAM') AND status IN ('PENDING','SENDING','ACCEPTED','RETRYABLE')")" == 0 ]]
  verify_message_schema
}

real_name_table_count() {
  mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='${DB_NAME}' AND table_name IN
    ('dms_member_real_name','dms_member_real_name_attempt')"
}

session_surface_column_count() {
  mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema='${DB_NAME}' AND table_name='dms_shop_member_session' AND column_name='surface'"
}

verify_real_name_schema() {
  [[ "$(real_name_table_count)" == 2 ]]
  [[ "$(session_surface_column_count)" == 1 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND table_name='dms_member_real_name' AND index_name='uk_member_real_name_account' AND non_unique=0")" == 1 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND table_name='dms_member_real_name' AND non_unique=0 AND column_name IN ('real_name','id_card')")" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_member_real_name')" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_member_real_name_attempt')" == 0 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_shop_member_session WHERE surface NOT IN ('legacy','public','team','integrated')")" == 0 ]]
}

feature_schema_object_count() {
  mysql_cmd -NBe "SELECT
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='dms_shop_product' AND column_name IN ('manual_new_arrival_enabled','manual_new_arrival_start_time','manual_new_arrival_end_time')) +
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='dms_tenant' AND column_name IN ('brand_culture_enabled','brand_culture_title','brand_culture_subtitle','brand_culture_cover_url','brand_culture_content')) +
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='dms_live_reservation')"
}

verify_feature_schema() {
  [[ "$(feature_schema_object_count)" == 9 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND table_name='dms_shop_product' AND index_name='idx_shop_product_manual_new_arrival'")" == 1 ]]
  [[ "$(mysql_cmd -NBe "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND table_name='dms_live_reservation' AND index_name IN ('uk_live_reservation_member','idx_live_reservation_notice')")" == 2 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COALESCE(SUM(brand_culture_enabled),0) FROM dms_tenant')" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COALESCE(SUM(manual_new_arrival_enabled),0) FROM dms_shop_product')" == 0 ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_live_reservation')" == 0 ]]
}

verify_packaged_notification_defaults() {
  local packaged_config
  packaged_config="$(unzip -p "$RELEASE_DIR/mall-distribution.jar" BOOT-INF/classes/application.yml)"
  for expected_default in \
    '${EXTERNAL_NOTIFICATION_ENABLED:false}' \
    '${EXTERNAL_NOTIFICATION_WORKER_ENABLED:false}' \
    '${NOTIFICATION_SMS_ALIYUN_ENABLED:false}' \
    '${NOTIFICATION_MOCK_ENABLED:false}' \
    '${NOTIFICATION_MOCK_APP_PUSH_ENABLED:false}' \
    '${NOTIFICATION_MOCK_MINI_PROGRAM_ENABLED:false}' \
    '${SHOP_REAL_NAME_ENABLED:false}'; do
    grep -Fq "$expected_default" <<< "$packaged_config"
  done
}

verify_notification_runtime_disabled() {
  local main_pid runtime_environment
  main_pid="$(systemctl show lingqimall-distribution.service --property=MainPID --value)"
  [[ "$main_pid" =~ ^[1-9][0-9]*$ ]]
  runtime_environment="$(tr '\0' '\n' < "/proc/$main_pid/environ")"
  if grep -Eiq '^(EXTERNAL_NOTIFICATION_ENABLED|EXTERNAL_NOTIFICATION_WORKER_ENABLED|NOTIFICATION_SMS_ALIYUN_ENABLED|NOTIFICATION_MOCK_ENABLED|NOTIFICATION_MOCK_APP_PUSH_ENABLED|NOTIFICATION_MOCK_MINI_PROGRAM_ENABLED|SHOP_REAL_NAME_ENABLED)=(true|1)$' <<< "$runtime_environment"; then
    echo "external notification and real-name runtime gates must remain disabled" >&2
    return 1
  fi
}

verify_surface_nginx() {
  grep -Fq 'map $host $shop_surface {' /etc/nginx/conf.d/lingqimall.conf
  grep -Fq 'default public;' /etc/nginx/conf.d/lingqimall.conf
  grep -Fq 'www.lingqimall.com team;' /etc/nginx/conf.d/lingqimall.conf
  [[ "$(grep -Fc 'proxy_set_header X-Shop-Surface' /etc/nginx/conf.d/lingqimall.conf)" -ge 5 ]]
}

verify_previous_public_manifests() {
  local public_manifest team_manifest admin_manifest manifest application
  public_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?preflight=$EXPECTED_BUILD_ID")"
  team_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://www.lingqimall.com/version.json?preflight=$EXPECTED_BUILD_ID")"
  admin_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/version.json?preflight=$EXPECTED_BUILD_ID")"
  for manifest_spec in "$public_manifest|storefront-public" "$team_manifest|team-h5" "$admin_manifest|admin"; do
    manifest="${manifest_spec%|*}"
    application="${manifest_spec##*|}"
    grep -q "\"version\": \"$EXPECTED_PREVIOUS_VERSION\"" <<< "$manifest"
    grep -q "\"gitCommit\": \"$EXPECTED_PREVIOUS_GIT_COMMIT\"" <<< "$manifest"
    grep -q "\"buildId\": \"$EXPECTED_PREVIOUS_BUILD_ID\"" <<< "$manifest"
    grep -q "\"application\": \"$application\"" <<< "$manifest"
  done
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

verify_backup_restoreability() {
  local verify_db=mall_distribution_release_verify_1088
  local verify_create_sql="${DB_CREATE_SQL/\`$DB_NAME\`/\`$verify_db\`}"
  (
    trap 'mysql_root -e "DROP DATABASE IF EXISTS \`mall_distribution_release_verify_1088\`" >/dev/null 2>&1 || true' EXIT
    mysql_root -e "DROP DATABASE IF EXISTS \`${verify_db}\`; ${verify_create_sql};"
    gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql --protocol=socket -uroot "$verify_db"
    [[ "$(mysql --protocol=socket -uroot "$verify_db" -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_PREVIOUS_MIGRATIONS" ]]
    [[ "$(mysql --protocol=socket -uroot "$verify_db" -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
    [[ "$(mysql_root -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${verify_db}' AND table_name IN ('dms_member_message','dms_message_template','dms_message_channel_config','dms_message_delivery_task')")" == 4 ]]
    [[ "$(mysql_root -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${verify_db}' AND table_name IN ('dms_message_delivery_attempt','dms_message_cost_budget','dms_message_recipient_authorization','dms_message_delivery_receipt')")" == 4 ]]
    [[ "$(mysql_root -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${verify_db}' AND table_name IN ('dms_member_real_name','dms_member_real_name_attempt')")" == 2 ]]
    [[ "$(mysql_root -NBe "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${verify_db}' AND table_name='dms_shop_member_session' AND column_name='surface'")" == 1 ]]
    [[ "$(mysql_root -NBe "SELECT (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${verify_db}' AND table_name='dms_shop_product' AND column_name IN ('manual_new_arrival_enabled','manual_new_arrival_start_time','manual_new_arrival_end_time')) + (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${verify_db}' AND table_name='dms_tenant' AND column_name IN ('brand_culture_enabled','brand_culture_title','brand_culture_subtitle','brand_culture_cover_url','brand_culture_content')) + (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${verify_db}' AND table_name='dms_live_reservation')")" == 9 ]]
    [[ "$(mysql --protocol=socket -uroot "$verify_db" -NBe 'SELECT COUNT(*) FROM dms_message_template')" == 15 ]]
    [[ "$(mysql --protocol=socket -uroot "$verify_db" -NBe 'SELECT COUNT(*) FROM dms_message_channel_config')" == 15 ]]
    [[ "$(mysql --protocol=socket -uroot "$verify_db" -NBe 'SELECT COALESCE(SUM(sms_enabled),0)+COALESCE(SUM(app_push_enabled),0)+COALESCE(SUM(mini_program_enabled),0) FROM dms_message_channel_config')" == 0 ]]
    [[ "$(database_counts_for "$verify_db")" == "$BEFORE_COUNTS" ]]
  )
}

restore_database_from_backup() {
  verify_backup "$BACKUP_BEFORE"
  [[ -n "$DB_CREATE_SQL" ]]
  mysql_root -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`; ${DB_CREATE_SQL};"
  gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql_cmd
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_PREVIOUS_MIGRATIONS" ]]
  [[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
  [[ "$(message_table_count)" == 4 ]]
  verify_notification_kernel
  verify_real_name_schema
  verify_feature_schema
  [[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
  [[ "$(plaintext_sensitive_value_count)" == 0 ]]
}

cleanup_staging() {
  [[ -n "$NEW_ADMIN" && -d "$NEW_ADMIN" ]] && rm -rf -- "$NEW_ADMIN"
  [[ -n "$NEW_SHOP" && -d "$NEW_SHOP" ]] && rm -rf -- "$NEW_SHOP"
  [[ -n "$NEW_TEAM" && -d "$NEW_TEAM" ]] && rm -rf -- "$NEW_TEAM"
}

rollback() {
  local code=$?
  local database_recovered=1
  set +e
  if [[ "$code" != 0 ]]; then
    echo "1.0.88 release failed; entering bounded recovery" >&2
    if [[ "$DB_MUTATED" == 1 || "$APP_MUTATED" == 1 ]]; then
      systemctl stop lingqimall-distribution.service >/dev/null 2>&1 || true
    fi
    if [[ "$DB_MUTATED" == 1 && "$MIGRATION_COMPLETED" == 0 ]]; then
      if restore_database_from_backup; then
        echo "database restored from verified pre-release backup: $BACKUP_BEFORE" >&2
      else
        database_recovered=0
        echo "CRITICAL: database restore failed; application remains stopped for manual recovery" >&2
      fi
    fi
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
      [[ -s "$ROLLBACK_DIR/lingqimall.conf" ]] && install -m 0644 "$ROLLBACK_DIR/lingqimall.conf" /etc/nginx/conf.d/lingqimall.conf
      [[ -s "$ROLLBACK_DIR/00-lingqimall-limits.conf" ]] && install -m 0644 "$ROLLBACK_DIR/00-lingqimall-limits.conf" /etc/nginx/conf.d/00-lingqimall-limits.conf
      nginx -t && systemctl reload nginx || true
    fi
    if [[ "$APP_MUTATED" == 1 ]]; then
      [[ -s "$ROLLBACK_DIR/mall-distribution.jar" ]] && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
      [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION"
      if [[ "$database_recovered" == 1 ]]; then systemctl start lingqimall-distribution.service || true; fi
    fi
    if [[ "$BACKUP_TOOL_MUTATED" == 1 && -s "$ROLLBACK_DIR/lingqimall-backup" ]]; then
      install -m 0750 "$ROLLBACK_DIR/lingqimall-backup" /usr/local/sbin/lingqimall-backup || true
    fi
  fi
  cleanup_staging
  exit "$code"
}
trap rollback EXIT

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.88 ]]
for file in mall-distribution.jar admin.tar.gz shop.tar.gz team.tar.gz integrated.tar.gz VERSION RELEASE_MANIFEST.json SHA256SUMS production-backup.sh db-migrate.sh lingqimall.conf lingqimall-security.conf release.sh; do
  [[ -s "$RELEASE_DIR/$file" ]]
done
[[ -x "$RELEASE_DIR/production-backup.sh" && -x "$RELEASE_DIR/db-migrate.sh" ]]
[[ "$(find "$RELEASE_DIR/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | wc -l)" == "$EXPECTED_MIGRATIONS" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
[[ "$(sha256sum "$RELEASE_DIR/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
tar -tzf "$RELEASE_DIR/integrated.tar.gz" | grep -Fx './version.json' >/dev/null
grep -Fq '"version": "1.0.88"' "$RELEASE_DIR/RELEASE_MANIFEST.json"
grep -Fq "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$RELEASE_DIR/RELEASE_MANIFEST.json"
grep -Fq "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$RELEASE_DIR/RELEASE_MANIFEST.json"
verify_packaged_notification_defaults

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
verify_notification_runtime_disabled
verify_previous_public_manifests
BEFORE_COUNTS="$(database_counts)"
DB_CREATE_SQL="$(mysql_root -NBe "SELECT CONCAT('CREATE DATABASE \`${DB_NAME}\` CHARACTER SET ',default_character_set_name,' COLLATE ',default_collation_name) FROM information_schema.schemata WHERE schema_name='${DB_NAME}'")"
ENCRYPTION_ENV_SHA="$(sha256sum "$APP_ROOT/config/data-encryption.env" | awk '{print $1}')"
ENCRYPTION_DROPIN_SHA="$(sha256sum /etc/systemd/system/lingqimall-distribution.service.d/data-encryption.conf | awk '{print $1}')"
[[ -n "$DB_CREATE_SQL" ]]
[[ "$(plaintext_sensitive_value_count)" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_PREVIOUS_MIGRATIONS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
verify_notification_kernel
verify_real_name_schema
verify_feature_schema
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE')" == 0 ]]

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.88.XXXXXX)"
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
verify_backup_restoreability
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.88.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.88.XXXXXX")"
NEW_TEAM="$(mktemp -d "$APP_ROOT/nginx/.team-v1.0.88.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
tar -xzf "$RELEASE_DIR/team.tar.gz" -C "$NEW_TEAM"
for manifest_spec in "$NEW_ADMIN/version.json:admin" "$NEW_SHOP/version.json:storefront-public" "$NEW_TEAM/version.json:team-h5"; do
  manifest="${manifest_spec%%:*}"
  application="${manifest_spec##*:}"
  grep -q '"version": "1.0.88"' "$manifest"
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
DB_MUTATED=1
MIGRATION_ROOT_DIR="$RELEASE_DIR" DB_AUTH_MODE=socket DB_USER=root DB_NAME="$DB_NAME" "$RELEASE_DIR/db-migrate.sh" apply
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_MIGRATIONS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
verify_message_schema
verify_notification_kernel
verify_real_name_schema
verify_feature_schema
MIGRATION_COMPLETED=1

install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start lingqimall-distribution.service
wait_for_health
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
[[ "$(plaintext_sensitive_value_count)" == 0 ]]
verify_message_schema
verify_notification_kernel
verify_real_name_schema
verify_feature_schema
verify_notification_runtime_disabled
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
verify_surface_nginx

public_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?release=$EXPECTED_BUILD_ID")"
team_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://www.lingqimall.com/version.json?release=$EXPECTED_BUILD_ID")"
admin_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/version.json?release=$EXPECTED_BUILD_ID")"
for manifest_spec in "$public_manifest|storefront-public" "$team_manifest|team-h5" "$admin_manifest|admin"; do
  manifest="${manifest_spec%|*}"
  application="${manifest_spec##*|}"
  grep -q '"version": "1.0.88"' <<< "$manifest"
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
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/brand-culture' | grep -q '"code":200'
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/shop/live-reservations')" == 401 ]]
sms_login_probe="$(curl --http1.1 -sS --max-time 12 -H 'Content-Type: application/json' --data '{"phone":"1"}' 'https://lingqimall.com/api/sms/send/login')"
grep -q '请输入正确的11位手机号' <<< "$sms_login_probe"
product_response="$(curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/products?pageNum=1&pageSize=1')"
for forbidden in costPrice costAmount bvValue safetyStock shippingAddress shippingAddressId deliveryAddress returnAddressId freightTemplateId repurchasePrice repurchasePv repurchaseEnabled repurchaseConfig settlementPrice merchantId; do
  ! grep -q "\"$forbidden\"" <<< "$product_response"
done
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/shop/messages/unread')" == 401 ]]
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/shop/admin/message-operations/templates')" == 401 ]]
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/shop/real-name/status')" == 401 ]]
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://www.lingqimall.com/api/shop/real-name/status')" == 401 ]]
transfer_probe='{"recipientPhone":"13800000000","amount":1,"paymentPassword":"000000"}'
# 资金接口在进入会话和端面校验前先执行加密载荷门禁，匿名明文探针固定返回 400，且不得产生转账。
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 -H 'Content-Type: application/json' --data "$transfer_probe" 'https://lingqimall.com/api/shop/wallet/transfers')" == 400 ]]
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 -H 'Content-Type: application/json' --data "$transfer_probe" 'https://www.lingqimall.com/api/shop/wallet/transfers')" == 400 ]]
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

[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
[[ "$(plaintext_sensitive_value_count)" == 0 ]]
verify_message_schema
verify_notification_kernel
verify_real_name_schema
verify_feature_schema
verify_notification_runtime_disabled
verify_surface_nginx
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
  echo "1.0.88 startup log verification failed" >&2
  exit 1
fi

APP_MUTATED=0
STATIC_MUTATED=0
NGINX_MUTATED=0
BACKUP_TOOL_MUTATED=0
DB_MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER build=$EXPECTED_BUILD_ID core-counts=$BEFORE_COUNTS migrations=$EXPECTED_MIGRATIONS display-workbench=multi-layout category-guide=A:B:C compliance-locks=server-enforced message-seeds=15:15 notification-tables=4 budgets=19:off authorizations=0 external-channels=off real-name=off real-name-records=0 session-surface=on transfer=integrated-only brand-culture=off live-reservations=0 encryption-preserved=yes jar=$EXPECTED_JAR_SHA256"
