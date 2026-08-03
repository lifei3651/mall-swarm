#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
DB_USER=mall_user
JAR=/tmp/mall-distribution-20260719.jar
ADMIN=/tmp/lingqimall-admin-20260719.tar.gz
SHOP=/tmp/lingqimall-shop-20260719.tar.gz
MIGRATION=/tmp/20260719_tenant_legal_config.sql
EXPECTED_JAR=766b3ffb561454937cb95a088227aff5bf5c3c88b081549a9b0a7d6e12a654a9
EXPECTED_ADMIN=a95213f53876bd2d9cba308341c0bc3f8c4b061d28451e80f83b4df335b9301b
EXPECTED_SHOP=612a7278b60bce403ba99196f516db657ad7146859125d59cf0914a49072b04a
EXPECTED_MIGRATION=81ed0315adba851657a57a9b7e29d1ce9ae3dc274260ba25a1d73b5ae661c846
ROLLBACK=/tmp/lingqimall-rollback-20260719
DEPLOYED=0

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -
echo "$EXPECTED_MIGRATION  $MIGRATION" | sha256sum -c -

/usr/local/sbin/lingqimall-backup
rm -rf "$ROLLBACK"
install -d -m 0700 "$ROLLBACK"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK/mall-distribution.jar"
tar -czf "$ROLLBACK/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .

rollback() {
  code=$?
  if [[ "$code" != 0 && "$DEPLOYED" == 1 ]]; then
    echo "deployment failed, restoring application files" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
    tar -xzf "$ROLLBACK/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    tar -xzf "$ROLLBACK/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

DB_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL=(mysql -h127.0.0.1 -u"$DB_USER" "$DB_NAME")

systemctl stop lingqimall-distribution.service
DEPLOYED=1
MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" < "$MIGRATION"
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
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

nginx -t
systemctl reload nginx

column_count=$(MYSQL_PWD="$DB_PASSWORD" "${MYSQL[@]}" -N -e "
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='dms_tenant'
AND COLUMN_NAME IN ('company_address','service_phone','service_email','icp_number','police_record_number',
'police_record_url','business_license_url','user_agreement','privacy_policy','after_sale_policy');")
[[ "$column_count" == 10 ]]

curl -fsS https://lingqimall.com/api/shop/legal-config | grep -q '"code":200'
curl -fsS https://lingqimall.com/ | grep -q '<div id="app">'
curl -fsS https://lingqimall.com/admin/ | grep -q '<div id="app">'
ss -lnt | grep -q '127.0.0.1]:8086\|127.0.0.1:8086'

echo "legal_columns=$column_count"
sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/nginx/admin/index.html" "$APP_ROOT/nginx/shop/index.html"
rm -rf "$ROLLBACK"
rm -f "$JAR" "$ADMIN" "$SHOP" "$MIGRATION"
DEPLOYED=0
trap - EXIT
echo deployment-complete
