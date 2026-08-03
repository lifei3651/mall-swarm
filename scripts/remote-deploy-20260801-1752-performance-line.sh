#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260801-1752
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260801-1752

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"

EXPECTED_JAR=5226620b910d7356cb05de7eae389a6fb83610e974c090a9be0f4f88865feef9
EXPECTED_ADMIN=6e3b00f892e18bf6899181bd7f68502c27ac012811801dcf34a1967475eb388a

MUTATED=0

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring previous backend and admin release" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

DB_CHECK_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
MYSQL_PWD="$DB_CHECK_PASSWORD" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe "SELECT 1" mall_distribution | grep -qx '1'
unset DB_CHECK_PASSWORD MYSQL_PWD

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .

MUTATED=1
systemctl stop lingqimall-distribution.service
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
chown -R www-data:www-data "$APP_ROOT/nginx/admin"

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

line_records_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/agent/line-change-applications')
performance_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/performance/overview?memberKey=M00000001&startDate=2026-07-03&endDate=2026-08-01')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  https://lingqimall.com/api/actuator/health)
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' http://lingqimall.com/)
[[ "$line_records_status" == 401 ]]
[[ "$performance_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

grep -R -Fq '所有拥有移线管理权限的管理员都能查看全部移线记录' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '默认近30天（含今天）' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '当前没有已计入的本人订单或团队订单业绩' "$APP_ROOT/nginx/admin/assets"

DB_CHECK_PASSWORD=$(awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml")
member_no_sample=$(MYSQL_PWD="$DB_CHECK_PASSWORD" mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u mall_user -NBe \
  "SELECT CONCAT('M', LPAD(TRIM(CAST(id AS CHAR(32))), 8, '0')) FROM dms_shop_member ORDER BY id LIMIT 1" \
  mall_distribution)
unset DB_CHECK_PASSWORD
[[ "$member_no_sample" =~ ^M[0-9]+$ ]]

curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^content-security-policy:'
curl -fsSI --max-time 12 https://lingqimall.com/ | grep -qi '^strict-transport-security:'
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo deployment-complete
