#!/usr/bin/env bash
set -euo pipefail

# 只读读取本机 Actuator 聚合指标；不访问业务明细、不写数据库，也不输出订单号或手机号。
PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:8086/actuator/prometheus}"
WECHAT_MAX_AGE_SECONDS="${WECHAT_MAX_AGE_SECONDS:-900}"
BONUS_MAX_AGE_SECONDS="${BONUS_MAX_AGE_SECONDS:-600}"
ERP_MAX_AGE_SECONDS="${ERP_MAX_AGE_SECONDS:-1800}"
metrics_file="$(mktemp)"
trap 'rm -f "$metrics_file"' EXIT

if ! curl --fail --silent --show-error --max-time 10 "$PROMETHEUS_URL" >"$metrics_file"; then
  echo "FAIL metrics_endpoint_unavailable"
  exit 1
fi

metric_value() {
  local metric="$1" label_key="$2" label_value="$3"
  awk -v metric="$metric" -v label="${label_key}=\"${label_value}\"" '
    $1 ~ ("^" metric "\\{") && index($1, label) { print $2; exit }
  ' "$metrics_file"
}

greater_than() {
  awk -v actual="$1" -v threshold="$2" 'BEGIN { exit !(actual + 0 > threshold + 0) }'
}

equals_one() {
  awk -v actual="$1" 'BEGIN { exit !(actual + 0 == 1) }'
}

failures=()

check_available() {
  local type="$1" source="$2" value
  value="$(metric_value mall_business_monitor_available source "$source")"
  if [[ -z "$value" ]]; then
    failures+=("missing:${type}/${source}")
  elif ! equals_one "$value"; then
    failures+=("unavailable:${type}/${source}")
  fi
}

check_task() {
  local task="$1" max_age="$2" backlog age terminal
  check_available task "$task"
  backlog="$(metric_value mall_business_task_backlog task "$task")"
  age="$(metric_value mall_business_task_oldest_age_seconds task "$task")"
  terminal="$(metric_value mall_business_task_failures task "$task")"
  if [[ -z "$backlog" || -z "$age" || -z "$terminal" ]]; then
    failures+=("missing:task/${task}/aggregate")
    return
  fi
  echo "task=${task} backlog=${backlog} oldest_age_seconds=${age} terminal_failures=${terminal}"
  if greater_than "$age" "$max_age"; then failures+=("old_backlog:${task}"); fi
  if greater_than "$terminal" 0; then failures+=("terminal_failures:${task}"); fi
  return 0
}

check_operation() {
  local operation="$1" count age
  check_available operation "$operation"
  count="$(metric_value mall_business_operation_timed_out operation "$operation")"
  age="$(metric_value mall_business_operation_oldest_age_seconds operation "$operation")"
  if [[ -z "$count" || -z "$age" ]]; then
    failures+=("missing:operation/${operation}/aggregate")
    return
  fi
  echo "operation=${operation} timed_out=${count} oldest_age_seconds=${age}"
  if greater_than "$count" 0; then failures+=("timed_out:${operation}"); fi
  return 0
}

check_task wechat_shipping_sync "$WECHAT_MAX_AGE_SECONDS"
check_task wechat_logistics_follow "$WECHAT_MAX_AGE_SECONDS"
check_task bonus_calculation "$BONUS_MAX_AGE_SECONDS"
check_task erp_sync "$ERP_MAX_AGE_SECONDS"
check_operation refund
check_operation withdrawal_payout

check_available ledger merchant_balance
ledger_mismatches="$(metric_value mall_business_ledger_mismatches ledger merchant_balance)"
if [[ -z "$ledger_mismatches" ]]; then
  failures+=("missing:ledger/merchant_balance/aggregate")
else
  echo "ledger=merchant_balance mismatches=${ledger_mismatches}"
  if greater_than "$ledger_mismatches" 0; then failures+=("mismatch:merchant_balance"); fi
fi

if ((${#failures[@]})); then
  echo "FAIL ${failures[*]}"
  exit 1
fi

echo "OK business_aggregates_healthy"
