#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  echo "必须使用 root 执行服务加固安装" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UNIT_SOURCE="$SCRIPT_DIR/lingqimall-distribution.service"
UNIT_TARGET="/etc/systemd/system/lingqimall-distribution.service"

[[ -f "$UNIT_SOURCE" ]] || { echo "缺少 systemd 服务模板：$UNIT_SOURCE" >&2; exit 1; }
[[ -f /opt/lingqimall/app/mall-distribution.jar ]] || { echo "后端程序包不存在" >&2; exit 1; }
[[ -f /opt/lingqimall/config/application.yml ]] || { echo "生产配置不存在" >&2; exit 1; }

if ! getent group lingqimall >/dev/null; then
  groupadd --system lingqimall
fi
if ! id lingqimall >/dev/null 2>&1; then
  useradd --system --gid lingqimall --home-dir /nonexistent --shell /sbin/nologin lingqimall
fi

install -d -o lingqimall -g lingqimall -m 0711 /opt/lingqimall/uploads
install -d -o lingqimall -g lingqimall -m 0755 /opt/lingqimall/uploads/products
install -d -o lingqimall -g lingqimall -m 0700 /opt/lingqimall/uploads/private
install -d -o lingqimall -g lingqimall -m 0750 /opt/lingqimall/logs
install -d -o lingqimall -g lingqimall -m 0750 /opt/lingqimall/logs/distribution
install -d -o lingqimall -g lingqimall -m 0750 /var/logs/spring.log
install -d -o lingqimall -g lingqimall -m 0750 /var/logs/spring.log/debug
install -d -o lingqimall -g lingqimall -m 0750 /var/logs/spring.log/error
chown -R lingqimall:lingqimall /opt/lingqimall/uploads /opt/lingqimall/logs
chmod 0711 /opt/lingqimall/uploads
chmod 0755 /opt/lingqimall/uploads/products
chmod 0700 /opt/lingqimall/uploads/private
find /opt/lingqimall/uploads/products -maxdepth 1 -type f -exec chmod 0644 {} +
chown -R lingqimall:lingqimall /var/logs/spring.log
chown root:lingqimall /opt/lingqimall/app /opt/lingqimall/config
chmod 0750 /opt/lingqimall/app /opt/lingqimall/config
chown root:lingqimall /opt/lingqimall/app/mall-distribution.jar
chmod 0640 /opt/lingqimall/app/mall-distribution.jar
chown root:lingqimall /opt/lingqimall/config/application.yml
chmod 0640 /opt/lingqimall/config/application.yml
runuser -u lingqimall -- test -r /opt/lingqimall/app/mall-distribution.jar
runuser -u lingqimall -- test -r /opt/lingqimall/config/application.yml
install -o root -g root -m 0644 "$UNIT_SOURCE" "$UNIT_TARGET"

systemctl daemon-reload
systemctl restart lingqimall-distribution.service
systemctl is-active --quiet lingqimall-distribution.service

unit_user="$(systemctl show lingqimall-distribution.service -p User --value)"
no_new_privileges="$(systemctl show lingqimall-distribution.service -p NoNewPrivileges --value)"
protect_system="$(systemctl show lingqimall-distribution.service -p ProtectSystem --value)"
[[ "$unit_user" == "lingqimall" ]] || { echo "服务仍未使用 lingqimall 用户" >&2; exit 1; }
[[ "$no_new_privileges" == "yes" ]] || { echo "NoNewPrivileges 未生效" >&2; exit 1; }
[[ "$protect_system" == "strict" ]] || { echo "ProtectSystem=strict 未生效" >&2; exit 1; }

echo "lingqimall-distribution 已切换为非 root 安全运行"
