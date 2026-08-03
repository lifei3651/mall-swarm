#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260722-security-business-hardening
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260722-security-business-hardening

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"
NGINX_SITE="$RELEASE_DIR/lingqimall.conf"
NGINX_SECURITY="$RELEASE_DIR/lingqimall-security.conf"

EXPECTED_JAR=3657fd609a8cd65d26ccdf6af3d48e20e9ae52025b081595af40cf7f34124ea9
EXPECTED_ADMIN=b4c6487cc30c7adb084f4a6274ec28bf17f0896a04bbcbafebf567ef0d573483
EXPECTED_SHOP=b44afd559e8f1583cc63aa036c0676500188896bfccaa693e1669e0b0aea7b99
EXPECTED_NGINX_SITE=92b1709ed423bcc714f562f6f2858c38596cc0a324c707db874051ec912b687e
EXPECTED_NGINX_SECURITY=afd5e39f6b16ff7a96665349798a141d59a9a15af80803b305b41b88f935b237

MUTATED=0

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring previous release" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    install -m 0600 "$ROLLBACK_DIR/application.yml" "$APP_ROOT/config/application.yml" || true
    install -m 0644 "$ROLLBACK_DIR/lingqimall.conf" /etc/nginx/sites-enabled/lingqimall.conf || true
    install -m 0644 "$ROLLBACK_DIR/lingqimall-security.conf" /etc/nginx/conf.d/lingqimall-security.conf || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
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
echo "$EXPECTED_NGINX_SITE  $NGINX_SITE" | sha256sum -c -
echo "$EXPECTED_NGINX_SECURITY  $NGINX_SECURITY" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

DB_CHECK_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL_PWD="$DB_CHECK_PASSWORD" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe "SELECT 1" mall_distribution | grep -qx '1'
unset DB_CHECK_PASSWORD MYSQL_PWD
SSL_ACCEPTS_BEFORE=$(mysql -NBe "SHOW GLOBAL STATUS LIKE 'Ssl_accepts';" | awk '{print $2}')

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
cp "$APP_ROOT/config/application.yml" "$ROLLBACK_DIR/application.yml"
cp /etc/nginx/sites-enabled/lingqimall.conf "$ROLLBACK_DIR/lingqimall.conf"
cp /etc/nginx/conf.d/lingqimall-security.conf "$ROLLBACK_DIR/lingqimall-security.conf"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .

MUTATED=1
grep -Fq 'useSSL=false&allowPublicKeyRetrieval=true' "$APP_ROOT/config/application.yml"
sed -i 's/useSSL=false&allowPublicKeyRetrieval=true/sslMode=REQUIRED/' "$APP_ROOT/config/application.yml"
grep -Fq 'sslMode=REQUIRED' "$APP_ROOT/config/application.yml"

install -m 0644 "$NGINX_SITE" /etc/nginx/sites-enabled/lingqimall.conf
install -m 0644 "$NGINX_SECURITY" /etc/nginx/conf.d/lingqimall-security.conf
nginx -t

systemctl stop lingqimall-distribution.service
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
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

systemctl reload nginx

curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop' | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/api/shop/home | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/ | grep -q '<div id="app">'
curl -fsS --max-time 12 https://lingqimall.com/admin/ | grep -q '<div id="app">'

wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' https://lingqimall.com/api/shop/wallet/flows)
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' https://lingqimall.com/api/actuator/health)
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' http://lingqimall.com/)
[[ "$wallet_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^content-security-policy:'
curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^strict-transport-security:'
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx

SSL_ACCEPTS_AFTER=$(mysql -NBe "SHOW GLOBAL STATUS LIKE 'Ssl_accepts';" | awk '{print $2}')
[[ "$SSL_ACCEPTS_AFTER" -gt "$SSL_ACCEPTS_BEFORE" ]]

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
