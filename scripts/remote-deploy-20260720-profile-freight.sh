#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
JAR=/tmp/mall-distribution-20260720-profile-freight.jar
ADMIN=/tmp/lingqimall-admin-20260720-profile-freight.tar.gz
SHOP=/tmp/lingqimall-shop-20260720-profile-freight.tar.gz
EXPECTED_JAR=31e469966551f080b263d937d269f99538405595e7638b3d2032b7de0ca73263
EXPECTED_ADMIN=e7e28e341dd709f8525167f9689a39c8ce584a41b341582d761ee051aad1bbba
EXPECTED_SHOP=9a3e82e4d05eb61c0d1f7d5f8fd6010a4bf1369448d92b416f3f2bc5011cc941
ROLLBACK=/tmp/lingqimall-rollback-20260720-profile-freight
DEPLOYED=0

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -

/usr/local/sbin/lingqimall-backup
rm -rf "$ROLLBACK"
install -d -m 0700 "$ROLLBACK"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK/mall-distribution.jar"
tar -czf "$ROLLBACK/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .

rollback() {
  code=$?
  if [[ "$code" != 0 && "$DEPLOYED" == 1 ]]; then
    echo "deployment failed, restoring previous application files" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    tar -xzf "$ROLLBACK/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

systemctl stop lingqimall-distribution.service
DEPLOYED=1
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
password_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' -X PUT \
  -H 'Content-Type: application/json' -d '{}' https://lingqimall.com/api/shop/auth/password)
[[ "$password_status" == 401 ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx

sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/nginx/admin/index.html" "$APP_ROOT/nginx/shop/index.html"
rm -rf "$ROLLBACK"
rm -f "$JAR" "$ADMIN" "$SHOP" /tmp/remote-deploy-20260720-profile-freight.sh
DEPLOYED=0
trap - EXIT
echo deployment-complete
