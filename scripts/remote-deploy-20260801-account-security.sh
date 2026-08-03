#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260801-account-security
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260801-account-security

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"

EXPECTED_JAR=c9b35a15fdd08b02e94f18e85c8c257235e34d58a42dfc3605f7756317415971
EXPECTED_ADMIN=8b0a2cdcb07d28641b84761516bff82b3eb1068573bcc12ac669f0f43f63561b
EXPECTED_SHOP=da15da284cab071661ae1dcccfd1cc1c290edce2201105bea250de861970f939

MUTATED=0

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring previous release" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
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

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

DB_CHECK_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL_PWD="$DB_CHECK_PASSWORD" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe "SELECT 1" mall_distribution | grep -qx '1'
unset DB_CHECK_PASSWORD MYSQL_PWD

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .

MUTATED=1
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

nginx -t
systemctl reload nginx

curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop' | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/api/shop/home | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/ | grep -q '<div id="app">'
curl -fsS --max-time 12 https://lingqimall.com/admin/ | grep -q '<div id="app">'

login_password_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' -d '{}' https://lingqimall.com/api/shop/auth/password)
payment_password_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' -d '{}' https://lingqimall.com/api/shop/wallet/payment-password)
admin_phone_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' -d '{}' https://lingqimall.com/api/shop/admin/members/1/phone)
admin_password_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' -d '{}' https://lingqimall.com/api/shop/admin/members/1/login-password)
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' https://lingqimall.com/api/actuator/health)
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' http://lingqimall.com/)
[[ "$login_password_status" == 401 ]]
[[ "$payment_password_status" == 401 ]]
[[ "$admin_phone_status" == 401 ]]
[[ "$admin_password_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

grep -R -Fq '验证码将发送至' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '修改登录密码' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '修改会员登录手机号' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '重置会员登录密码' "$APP_ROOT/nginx/admin/assets"
curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^content-security-policy:'
curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^strict-transport-security:'
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'
ss -lnt | grep -q '127.0.0.1]:8086\|127.0.0.1:8086'

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
