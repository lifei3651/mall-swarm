#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${APP_ROOT:-/opt/lingqimall}"
LOG_DIR="$APP_ROOT/logs/health"
LOG_FILE="$LOG_DIR/health-check.log"
BACKUP_ROOT="$APP_ROOT/backups/full"
NOW="$(date '+%F %T')"
failures=()

install -d -m 0750 "$LOG_DIR"

for service in lingqimall-distribution.service nginx mysql redis-server; do
  systemctl is-active --quiet "$service" || failures+=("service:$service")
done

curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"' || failures+=("backend-health")
curl -fsS --max-time 12 https://lingqimall.com/ >/dev/null || failures+=("public-https")

disk_usage="$(df -P / | awk 'NR==2 {gsub("%", "", $5); print $5}')"
[[ "$disk_usage" -lt 85 ]] || failures+=("disk:${disk_usage}%")

available_mb="$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)"
[[ "$available_mb" -ge 150 ]] || failures+=("memory:${available_mb}MB")

latest_db="$(find "$BACKUP_ROOT" -mindepth 2 -maxdepth 2 -type f -name database.sql.gz -mmin -1800 -print -quit 2>/dev/null || true)"
[[ -n "$latest_db" ]] || failures+=("backup:older-than-30h")

if ((${#failures[@]})); then
  message="$NOW FAIL ${failures[*]}"
  echo "$message" >> "$LOG_FILE"
  logger -p local0.err -t lingqimall-health "$message"
  exit 1
fi

echo "$NOW OK disk=${disk_usage}% memory_available=${available_mb}MB" >> "$LOG_FILE"
find "$LOG_DIR" -type f -name '*.log' -size +10M -exec sh -c 'tail -n 5000 "$1" > "$1.tmp" && mv "$1.tmp" "$1"' _ {} \;
