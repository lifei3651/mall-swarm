#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.14-backend
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.14-backend
BACKUP_PATH=""
MUTATED=0

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "backend release failed; restoring previous application and version" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    systemctl start lingqimall-distribution.service || true
  fi
  exit "$code"
}
trap rollback EXIT

[[ -s "$RELEASE_DIR/mall-distribution.jar" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "1.0.14" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.13" ]]
echo "$RELEASE_DIR_EXPECTED_SHA  $RELEASE_DIR/mall-distribution.jar" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

/usr/local/sbin/lingqimall-backup
BACKUP_PATH=$(readlink -f "$APP_ROOT/backups/full/latest")

install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"

MUTATED=1
systemctl stop lingqimall-distribution.service
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
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
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 12 https://lingqimall.com/ >/dev/null
curl -fsS --max-time 12 https://lingqimall.com/admin/ >/dev/null
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.14" ]]

MUTATED=0
trap - EXIT
echo "release-success version=1.0.14 backup=$BACKUP_PATH jar_sha256=$RELEASE_DIR_EXPECTED_SHA"
