#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.private.yml"

[ -f "$ENV_FILE" ] || { echo "找不到客户 .env，请先执行 prepare" >&2; exit 1; }
[ -t 0 ] || { echo "创建管理员必须在交互式终端执行" >&2; exit 1; }
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q mysql | grep -q . \
  || { echo "MySQL 尚未启动，请先执行 apply" >&2; exit 1; }

admin_password=''
admin_password_confirm=''
cleanup() {
  stty echo 2>/dev/null || true
  unset admin_password admin_password_confirm password_b64
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

printf '首个后台管理员账号: '
IFS= read -r admin_username
case "$admin_username" in
  ''|*[!A-Za-z0-9_.-]*) echo "账号只能包含字母、数字、点、下划线和短横线" >&2; exit 2 ;;
esac
[ "${#admin_username}" -ge 4 ] && [ "${#admin_username}" -le 64 ] \
  || { echo "账号长度必须为4-64位" >&2; exit 2; }

printf '初始密码（10-64位，至少包含大小写字母、数字、符号中的三类）: '
stty -echo
IFS= read -r admin_password
stty echo
printf '\n再次输入初始密码: '
stty -echo
IFS= read -r admin_password_confirm
stty echo
printf '\n'

[ "$admin_password" = "$admin_password_confirm" ] || { echo "两次密码不一致" >&2; exit 2; }
[ "${#admin_password}" -ge 10 ] && [ "${#admin_password}" -le 64 ] \
  || { echo "密码长度必须为10-64位" >&2; exit 2; }
groups=0
printf %s "$admin_password" | grep -q '[a-z]' && groups=$((groups + 1)) || true
printf %s "$admin_password" | grep -q '[A-Z]' && groups=$((groups + 1)) || true
printf %s "$admin_password" | grep -q '[0-9]' && groups=$((groups + 1)) || true
printf %s "$admin_password" | grep -q '[^A-Za-z0-9]' && groups=$((groups + 1)) || true
[ "$groups" -ge 3 ] || { echo "密码复杂度不足" >&2; exit 2; }

username_b64=$(printf %s "$admin_username" | base64 | tr -d '\r\n')
password_b64=$(printf %s "$admin_password" | base64 | tr -d '\r\n')

existing=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -NBe "SELECT COUNT(*) FROM ${MYSQL_DATABASE}.dms_admin_user"')
[ "$existing" = "0" ] || { echo "后台管理员已存在，拒绝重复初始化" >&2; exit 1; }

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"' <<SQL
SET @bootstrap_username = CONVERT(FROM_BASE64('$username_b64') USING utf8mb4);
SET @bootstrap_password = CONVERT(FROM_BASE64('$password_b64') USING utf8mb4);
SET @bootstrap_salt = LOWER(REPLACE(UUID(), '-', ''));
INSERT INTO dms_admin_user
  (username, password_hash, salt, nickname, role_code, permissions, status, must_change_password)
VALUES
  (@bootstrap_username, SHA2(CONCAT(@bootstrap_password, ':', @bootstrap_salt), 256), @bootstrap_salt,
   '首个管理员', 'SUPER_ADMIN', '*', 1, 1);
SQL

echo "首个管理员已创建。首次登录后系统会强制改密，并自动迁移为 BCrypt。"
