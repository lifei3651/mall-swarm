#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/lingqimall
JAR=/tmp/mall-distribution-20260720.jar
ADMIN=/tmp/lingqimall-admin-20260720.tar.gz
SHOP=/tmp/lingqimall-shop-20260720.tar.gz
EXPECTED_JAR=85e12d5c778b01b3ef16f432227724ea8f31726e91c2192cd26e8060f971dfc3
EXPECTED_ADMIN=9f95a0eae01e334e017886ce657d72ecbba97cbd448c836f2042be73a231a66d
EXPECTED_SHOP=fcfdd04796e647d12cfcda7d7808dfb76f4767b16b29395baf06a9675187dc94
ROLLBACK=/tmp/lingqimall-rollback-20260720
DEPLOYED=0

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -

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
    rm -rf "$APP_ROOT/nginx/admin"/* "$APP_ROOT/nginx/shop"/*
    tar -xzf "$ROLLBACK/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    tar -xzf "$ROLLBACK/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

systemctl stop lingqimall-distribution.service
DEPLOYED=1
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

curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=shop' | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/api/shop/home | grep -q '"code":200'
curl -fsS --max-time 12 https://lingqimall.com/ | grep -q '<div id="app">'
curl -fsS --max-time 12 https://lingqimall.com/admin/ | grep -q '<div id="app">'
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx

sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/nginx/admin/index.html" "$APP_ROOT/nginx/shop/index.html"
rm -rf "$ROLLBACK"
rm -f "$JAR" "$ADMIN" "$SHOP" /tmp/remote-deploy-20260720-ui-orders.sh
DEPLOYED=0
trap - EXIT
echo deployment-complete
