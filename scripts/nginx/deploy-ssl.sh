#!/usr/bin/env bash
# =============================================================
# SSL 证书部署脚本（Let's Encrypt + Nginx）
# 用法：sudo bash deploy-ssl.sh your-domain.com your-email@example.com
# =============================================================
set -euo pipefail

DOMAIN="${1:?用法: sudo bash deploy-ssl.sh <域名> <邮箱>}"
EMAIL="${2:?请提供邮箱地址用于 Let's Encrypt 通知}"

echo "=== 1. 安装 certbot ==="
if command -v certbot &>/dev/null; then
    echo "certbot 已安装: $(certbot --version)"
else
    apt-get update && apt-get install -y certbot python3-certbot-nginx
fi

echo "=== 2. 停止 Nginx（释放 80 端口）==="
systemctl stop nginx || true

echo "=== 3. 申请证书 ==="
certbot certonly --standalone \
    -d "$DOMAIN" -d "www.$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email \
    --non-interactive

echo "=== 4. 复制 Nginx 配置 ==="
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cp "$SCRIPT_DIR/lingqimall-security.conf" /etc/nginx/conf.d/security.conf 2>/dev/null || true

echo "=== 5. 测试 Nginx 配置 ==="
nginx -t

echo "=== 6. 启动 Nginx ==="
systemctl start nginx

echo "=== 7. 设置证书自动续期 ==="
# Let's Encrypt 证书有效期 90 天，自动续期
cat > /etc/cron.d/certbot-renew <<'EOF'
0 3 * * 1 root certbot renew --quiet --deploy-hook "systemctl reload nginx"
EOF
chmod 644 /etc/cron.d/certbot-renew

echo "=== 8. 验证 HTTPS ==="
echo "请访问 https://$DOMAIN 验证证书是否生效"
echo "运行以下命令检查 TLS 配置："
echo "  curl -sI https://$DOMAIN | grep -i 'strict-transport'"
echo ""
echo "在线检测：https://www.ssllabs.com/ssltest/analyze.html?d=$DOMAIN"

echo ""
echo "=== 完成 ==="
echo "证书路径："
echo "  证书: /etc/letsencrypt/live/$DOMAIN/fullchain.pem"
echo "  私钥: /etc/letsencrypt/live/$DOMAIN/privkey.pem"
echo "  续期: /etc/cron.d/certbot-renew (每周一凌晨 3 点检查)"
