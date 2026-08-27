#!/usr/bin/env bash
# =============================================================
# SSL 安全验证脚本 — 检查部署后的 HTTPS 配置
# 用法：bash verify-ssl.sh [域名] [当前生产源站IP]
# =============================================================
set -euo pipefail

DOMAIN="${1:-lingqimall.com}"
ORIGIN_IP="${2:-}"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=../production-targets.sh
source "$SCRIPT_DIR/../production-targets.sh"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }

echo "=========================================="
echo "  灵启商城 SSL 安全验证"
echo "  域名: $DOMAIN"
echo "=========================================="
echo ""

# 1. 检查 HTTPS 是否可达
echo "--- 1. 连通性检查 ---"
if curl -sI "https://$DOMAIN" --max-time 10 | grep -q "200\|301\|302"; then
    pass "HTTPS 连接成功"
else
    fail "HTTPS 连接失败"
fi

# 2. 检查 HTTP → HTTPS 跳转
HTTP_CODE=$(curl -sI "http://$DOMAIN" --max-time 10 -o /dev/null -w '%{http_code}')
if [ "$HTTP_CODE" = "301" ]; then
    pass "HTTP → HTTPS 301 跳转正常"
else
    fail "HTTP 未正确跳转到 HTTPS (状态码: $HTTP_CODE)"
fi

# 3. 检查 HSTS 头
HSTS=$(curl -sI "https://$DOMAIN" --max-time 10 | grep -i "strict-transport-security" || true)
if [ -n "$HSTS" ]; then
    pass "HSTS 头已启用: $HSTS"
else
    fail "HSTS 头未配置"
fi

# 4. 检查安全头
echo ""
echo "--- 2. 安全响应头检查 ---"
HEADERS=$(curl -sI "https://$DOMAIN" --max-time 10)

for HEADER in "X-Content-Type-Options" "X-Frame-Options" "Referrer-Policy" "Permissions-Policy"; do
    if echo "$HEADERS" | grep -qi "$HEADER"; then
        pass "$HEADER 已配置"
    else
        fail "$HEADER 缺失"
    fi
done

# 5. 检查 TLS 版本
echo ""
echo "--- 3. TLS 协议检查 ---"
for VER in tls1 tls1_1; do
    if openssl s_client -connect "$DOMAIN:443" -"$VER" </dev/null 2>/dev/null | grep -q "Protocol.*TLSv"; then
        fail "不安全的 ${VER} 仍然启用"
    else
        pass "已禁用不安全的 ${VER}"
    fi
done

if openssl s_client -connect "$DOMAIN:443" -tls1_2 </dev/null 2>/dev/null | grep -q "Protocol.*TLSv1.2"; then
    pass "TLS 1.2 正常工作"
else
    warn "TLS 1.2 连接测试失败（可能是网络问题）"
fi

# 6. 检查证书有效期
echo ""
echo "--- 4. 证书检查 ---"
CERT_INFO=$(echo | openssl s_client -servername "$DOMAIN" -connect "$DOMAIN:443" 2>/dev/null | openssl x509 -noout -dates 2>/dev/null || true)
if [ -n "$CERT_INFO" ]; then
    EXPIRY=$(echo "$CERT_INFO" | grep "notAfter" | cut -d= -f2)
    pass "证书到期时间: $EXPIRY"

    # 检查是否在 30 天内过期
    EXPIRY_EPOCH=$(date -d "$EXPIRY" +%s 2>/dev/null || date -jf "%b %d %T %Y %Z" "$EXPIRY" +%s 2>/dev/null || echo 0)
    NOW_EPOCH=$(date +%s)
    DAYS_LEFT=$(( (EXPIRY_EPOCH - NOW_EPOCH) / 86400 ))
    if [ "$DAYS_LEFT" -lt 30 ]; then
        fail "证书将在 ${DAYS_LEFT} 天后过期，请立即续期！"
    else
        pass "证书还有 ${DAYS_LEFT} 天到期"
    fi
else
    warn "无法获取证书信息"
fi

# 7. 可选检查当前生产源站 IP 直接访问是否返回 404
echo ""
echo "--- 5. IP 直接访问检查 ---"
if [ -z "$ORIGIN_IP" ]; then
    warn "未显式提供当前生产源站IP，已跳过；不会再使用历史IP猜测"
else
    for retired_host in "${LINGQIMALL_RETIRED_HOSTS[@]}"; do
        if [ "$ORIGIN_IP" = "$retired_host" ]; then
            fail "拒绝检查已退役主机：$ORIGIN_IP"
            exit 1
        fi
    done
    IP_CODE=$(curl -sI "http://$ORIGIN_IP" --max-time 10 -o /dev/null -w '%{http_code}')
    if [ "$IP_CODE" = "404" ]; then
        pass "当前生产源站IP直接访问返回 404（安全）"
    else
        warn "当前生产源站IP直接访问返回 $IP_CODE（建议返回 404）"
    fi
fi

# 8. 检查生产调试入口是否关闭
echo ""
echo "--- 6. 调试入口检查 ---"
for DEBUG_PATH in "/api/v3/api-docs" "/api/swagger-ui.html" "/api/actuator/env"; do
    DEBUG_CODE=$(curl -sI "https://$DOMAIN$DEBUG_PATH" --max-time 10 -o /dev/null -w '%{http_code}')
    if [ "$DEBUG_CODE" = "404" ]; then
        pass "$DEBUG_PATH 已关闭"
    else
        fail "$DEBUG_PATH 仍可访问（状态码: $DEBUG_CODE）"
    fi
done

echo ""
echo "=========================================="
echo "  验证完成"
echo "=========================================="
