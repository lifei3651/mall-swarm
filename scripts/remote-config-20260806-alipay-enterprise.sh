#!/usr/bin/env bash
set -Eeuo pipefail

APP=/opt/lingqimall
STAMP=$(date +%Y%m%d_%H%M%S)
BACKUP="$APP/backups/full/$STAMP"
CONF=/etc/systemd/system/lingqimall-distribution.service.d/alipay.conf
OLD_CONF="$APP/config/alipay.conf.before-enterprise-$STAMP"
OLD_ENV="$APP/config/alipay.env.before-enterprise-$STAMP"
MUTATED=0

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "Alipay configuration failed; restoring previous configuration" >&2
    if [[ -f "$OLD_CONF" ]]; then install -m 0644 "$OLD_CONF" "$CONF"; fi
    if [[ -f "$OLD_ENV" ]]; then install -m 0600 "$OLD_ENV" "$APP/config/alipay.env"; fi
    systemctl daemon-reload || true
    systemctl restart lingqimall-distribution.service || true
  fi
  rm -f /tmp/alipay-private-new.pem /tmp/alipay-public.txt
  exit "$code"
}
trap rollback EXIT

[[ -s /tmp/alipay-private-new.pem ]]
[[ -s /tmp/alipay-public.txt ]]
install -m 0600 /tmp/alipay-private-new.pem "$APP/config/alipay-private-new.pem"
install -m 0644 /tmp/alipay-public.txt "$APP/config/alipay-public.txt"

/usr/local/sbin/lingqimall-backup
cp "$CONF" "$OLD_CONF"
if [[ -f "$APP/config/alipay.env" ]]; then cp "$APP/config/alipay.env" "$OLD_ENV"; fi

{
  printf 'ALIPAY_ENABLED=true\n'
  printf 'ALIPAY_APP_ID=2021006180653344\n'
  printf 'ALIPAY_PRIVATE_KEY='
  tr -d '\r\n' < "$APP/config/alipay-private-new.pem"
  printf '\nALIPAY_PUBLIC_KEY='
  tr -d '\r\n' < "$APP/config/alipay-public.txt"
  printf '\nALIPAY_NOTIFY_URL=https://lingqimall.com/api/pay/alipay/notify\n'
  printf 'ALIPAY_RETURN_URL=https://lingqimall.com/api/pay/alipay/return\n'
} > "$APP/config/alipay.env.new"
chmod 0600 "$APP/config/alipay.env.new"
mv "$APP/config/alipay.env.new" "$APP/config/alipay.env"
printf '[Service]\nEnvironmentFile=%s/config/alipay.env\n' "$APP" > "$CONF"
MUTATED=1

systemctl daemon-reload
systemctl restart lingqimall-distribution.service
for _ in $(seq 1 45); do
  if systemctl is-active --quiet lingqimall-distribution.service \
      && curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done
systemctl is-active --quiet lingqimall-distribution.service
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

MUTATED=0
trap - EXIT
rm -f /tmp/alipay-private-new.pem /tmp/alipay-public.txt
echo "alipay-configured app_id=2021006180653344 backup=$BACKUP health=UP"
