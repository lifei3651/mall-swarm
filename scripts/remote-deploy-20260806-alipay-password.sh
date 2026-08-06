#!/usr/bin/env bash
set -euo pipefail

APP=/opt/lingqimall
TS=20260806_081800
BACKUP="$APP/backups/full/$TS"

# This deployment only replaces built assets and the application JAR. It does
# not run business-data cleanup or database migrations.
/usr/local/sbin/lingqimall-backup
mkdir -p "$BACKUP" "$APP/releases/shop-$TS" "$APP/releases/admin-$TS"
cp -a "$APP/nginx/shop" "$BACKUP/shop"
cp -a "$APP/nginx/admin" "$BACKUP/admin"
cp -a "$APP/app/mall-distribution.jar" "$BACKUP/mall-distribution.jar"
cp -a "$APP/config/application.yml" "$BACKUP/application.yml"

tar -xzf /tmp/lingqimall-shop-20260806.tar.gz -C "$APP/releases/shop-$TS"
tar -xzf /tmp/lingqimall-admin-20260806.tar.gz -C "$APP/releases/admin-$TS"
chown -R www-data:www-data "$APP/releases/shop-$TS" "$APP/releases/admin-$TS"
mv "$APP/nginx/shop" "$APP/nginx/shop.previous.$TS"
mv "$APP/nginx/admin" "$APP/nginx/admin.previous.$TS"
mv "$APP/releases/shop-$TS" "$APP/nginx/shop"
mv "$APP/releases/admin-$TS" "$APP/nginx/admin"

gzip -dc /tmp/mall-distribution-20260806.jar.gz > "$APP/app/mall-distribution.jar.new"
chown root:root "$APP/app/mall-distribution.jar.new"
chmod 0644 "$APP/app/mall-distribution.jar.new"
sha256sum "$APP/app/mall-distribution.jar.new"
mv "$APP/app/mall-distribution.jar.new" "$APP/app/mall-distribution.jar"

nginx -t
systemctl reload nginx
systemctl restart lingqimall-distribution.service
for i in $(seq 1 15); do
  if systemctl is-active --quiet lingqimall-distribution.service \
      && curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 12 https://lingqimall.com/ >/tmp/shop-check.html
curl -fsS --max-time 12 https://lingqimall.com/admin/ >/tmp/admin-check.html
grep -q '<div id="app">' /tmp/shop-check.html
grep -q '<div id="app">' /tmp/admin-check.html
SHOP_ENTRY=$(grep -o '/assets/index-[^"]*\.js' /tmp/shop-check.html | head -1)
curl -fsS --max-time 12 "https://lingqimall.com$SHOP_ENTRY" >/tmp/shop-entry.js
test -s /tmp/shop-entry.js

printf 'release=%s\nbackup=%s\nshop_entry=%s\nhealth=' "$TS" "$BACKUP" "$SHOP_ENTRY"
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health
printf '\nservice='; systemctl is-active lingqimall-distribution.service
printf ' nginx='; systemctl is-active nginx
printf ' mysql='; systemctl is-active mysql
printf ' redis='; systemctl is-active redis-server
printf '\npassword-text='; grep -R -l '支付密码已设置' "$APP/nginx/shop/assets" | head -1
