#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

LEGACY_MODULES=(
  mall-admin
  mall-portal
  mall-search
  mall-auth
  mall-gateway
  mall-monitor
  mall-demo
)

for module in "${LEGACY_MODULES[@]}"; do
  if [[ -e "$PROJECT_ROOT/$module" ]]; then
    echo "ERROR: legacy module is present in active project: $module" >&2
    exit 1
  fi
  if grep -Fq "<module>$module</module>" "$PROJECT_ROOT/pom.xml"; then
    echo "ERROR: legacy module is declared in root pom.xml: $module" >&2
    exit 1
  fi
done

JAR_PATH="$PROJECT_ROOT/mall-distribution/target/mall-distribution-1.0-SNAPSHOT.jar"
if [[ -f "$JAR_PATH" ]]; then
  for module in "${LEGACY_MODULES[@]}"; do
    if unzip -Z1 "$JAR_PATH" | grep -Fq "BOOT-INF/lib/$module-"; then
      echo "ERROR: legacy module is packaged in mall-distribution JAR: $module" >&2
      exit 1
    fi
  done
fi

echo "OK: active module boundary contains only mall-distribution, mall-mbg and mall-common backend modules."
