#!/usr/bin/env bash
set -euo pipefail

# Run only against a disposable MySQL database, for example inside an isolated
# container: DB_AUTH_MODE=socket DB_USER=root DB_NAME=lingqi_lock_test bash ...
if [[ "${DB_AUTH_MODE:-}" != socket || ! "${DB_NAME:-}" =~ ^[A-Za-z0-9_]*_lock_test$ ]]; then
  echo "仅允许以 DB_AUTH_MODE=socket 在独立 *_lock_test 数据库执行此集成测试。" >&2
  exit 2
fi
DB_USER="${DB_USER:-root}"
RUNNER="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/db-migrate.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/mall-migration-lock-test.XXXXXX")"
RUNNER_PID=""
cleanup() {
  if [[ -n "$RUNNER_PID" ]]; then
    kill "$RUNNER_PID" 2>/dev/null || true
    wait "$RUNNER_PID" 2>/dev/null || true
  fi
  rm -f "$TEST_ROOT/document/db/migrations/V202609261010__hold_lock.sql" \
    "$TEST_ROOT/document/db/migrations/V202609261020__detect_lost_lock.sql" \
    "$TEST_ROOT/first.log" "$TEST_ROOT/second.log" "$TEST_ROOT/lost.log"
  rmdir "$TEST_ROOT/document/db/migrations" "$TEST_ROOT/document/db" \
    "$TEST_ROOT/runner-a" "$TEST_ROOT/runner-b" "$TEST_ROOT" 2>/dev/null || true
}
trap cleanup EXIT
mkdir -p "$TEST_ROOT/document/db/migrations" "$TEST_ROOT/runner-a" "$TEST_ROOT/runner-b"

mysql_test() {
  mysql --protocol=socket -u"$DB_USER" --batch --skip-column-names "$DB_NAME" -e "$1"
}
fail() {
  echo "$1" >&2
  for log in "$TEST_ROOT/first.log" "$TEST_ROOT/second.log" "$TEST_ROOT/lost.log"; do
    if [[ -f "$log" ]]; then echo "${log}:" >&2; tail -n 12 "$log" >&2; fi
  done
  exit 1
}
lock_owner() {
  mysql_test "SELECT IS_USED_LOCK('mall_schema:${DB_NAME}');"
}
wait_for_running_migration() {
  local log="$1" name="$2" owner=""
  for ((attempt=0; attempt<100; attempt++)); do
    owner="$(lock_owner)"
    if [[ "$owner" =~ ^[0-9]+$ ]] && grep -Fq "正在执行 $name" "$log"; then
      printf '%s' "$owner"
      return 0
    fi
    if ! kill -0 "$RUNNER_PID" 2>/dev/null; then break; fi
    sleep 0.1
  done
  return 1
}

history_exists="$(mysql_test "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='dms_schema_migration_history';")"
[[ "$history_exists" == 0 ]] || fail "测试数据库已有迁移记录表，请换一个空的 *_lock_test 数据库。"

printf 'DO SLEEP(4);\n' > "$TEST_ROOT/document/db/migrations/V202609261010__hold_lock.sql"
TMPDIR="$TEST_ROOT/runner-a" MIGRATION_ROOT_DIR="$TEST_ROOT" bash "$RUNNER" apply > "$TEST_ROOT/first.log" 2>&1 &
RUNNER_PID=$!
first_owner="$(wait_for_running_migration "$TEST_ROOT/first.log" V202609261010__hold_lock.sql)" || fail "第一个迁移未保持数据库锁。"

if TMPDIR="$TEST_ROOT/runner-b" MIGRATION_ROOT_DIR="$TEST_ROOT" bash "$RUNNER" apply > "$TEST_ROOT/second.log" 2>&1; then
  fail "第二个迁移在第一个执行期间错误地获得了数据库锁。"
else
  second_status=$?
fi
[[ "$second_status" == 3 ]] || fail "第二个迁移退出码应为 3，实际为 $second_status。"
grep -Fq '另一台主机正在对该数据库执行迁移' "$TEST_ROOT/second.log" || fail "第二个迁移没有报告锁冲突。"
[[ "$(lock_owner)" == "$first_owner" ]] || fail "第二个迁移意外释放了第一个迁移的锁。"
if ! wait "$RUNNER_PID"; then fail "第一个迁移执行失败。"; fi
RUNNER_PID=""
MIGRATION_ROOT_DIR="$TEST_ROOT" bash "$RUNNER" verify > /dev/null || fail "第一个迁移完成后校验失败。"

printf 'SELECT * FROM migration_lock_test_missing_table;\n' > "$TEST_ROOT/document/db/migrations/V202609261020__detect_lost_lock.sql"
if TMPDIR="$TEST_ROOT/runner-a" MIGRATION_ROOT_DIR="$TEST_ROOT" bash "$RUNNER" apply > "$TEST_ROOT/lost.log" 2>&1; then
  fail "错误 SQL 被误记为成功。"
else
  failed_sql_status=$?
fi
[[ "$failed_sql_status" == 5 ]] || fail "错误 SQL 的退出码应为 5，实际为 $failed_sql_status。"
failed_rows="$(mysql_test "SELECT COUNT(*) FROM dms_schema_migration_history WHERE version='202609261020' AND success=0;")"
[[ "$failed_rows" == 1 ]] || fail "错误 SQL 没有保留 success=0 迁移记录。"

# This is an empty disposable test database; clear only the synthetic failure
# row so the next scenario can exercise an unexpected connection loss.
mysql_test "DELETE FROM dms_schema_migration_history WHERE version='202609261020' AND success=0;"
printf 'DO SLEEP(4);\n' > "$TEST_ROOT/document/db/migrations/V202609261020__detect_lost_lock.sql"
TMPDIR="$TEST_ROOT/runner-a" MIGRATION_ROOT_DIR="$TEST_ROOT" bash "$RUNNER" apply > "$TEST_ROOT/lost.log" 2>&1 &
RUNNER_PID=$!
lost_owner="$(wait_for_running_migration "$TEST_ROOT/lost.log" V202609261020__detect_lost_lock.sql)" || fail "第二轮迁移没有进入执行阶段。"
mysql_test "KILL CONNECTION ${lost_owner};"
if wait "$RUNNER_PID"; then fail "持锁连接中断后迁移仍被误记为成功。"; fi
RUNNER_PID=""
failed_rows="$(mysql_test "SELECT COUNT(*) FROM dms_schema_migration_history WHERE version='202609261020' AND success=0;")"
[[ "$failed_rows" == 1 ]] || fail "持锁连接中断后没有保留 success=0 迁移记录。"
echo "迁移锁集成测试通过：并发执行被拒绝；错误 SQL 与持锁连接中断均留下失败记录。"
