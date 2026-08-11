#!/usr/bin/env python3
"""校验 Nginx 的订单/售后 SSE 代理配置，不连接任何业务接口。"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys


SSE_LOCATION = re.compile(
    r"location\s+~\s+\^/api/shop/\(admin/\)\?events/orders\$\s*\{"
)
REQUIRED_DIRECTIVES = {
    "proxy_http_version 1.1;": "HTTP/1.1 长连接",
    'proxy_set_header Connection "";': "清除 Connection 头",
    "proxy_request_buffering off;": "关闭请求缓冲",
    "proxy_buffering off;": "关闭响应缓冲",
    "proxy_cache off;": "关闭代理缓存",
    "proxy_send_timeout 35m;": "延长发送超时",
    "proxy_read_timeout 35m;": "延长读取超时",
    "gzip off;": "关闭事件流压缩",
    'add_header X-Accel-Buffering "no" always;': "显式禁止 Nginx 缓冲",
    'add_header Cache-Control "no-store" always;': "禁止客户端缓存事件流",
}


def read_config(path: str) -> tuple[str, str]:
    if path == "-":
        return "stdin/nginx -T", sys.stdin.read()
    config_path = pathlib.Path(path)
    return str(config_path), config_path.read_text(encoding="utf-8")


def extract_block(content: str) -> str | None:
    match = SSE_LOCATION.search(content)
    if not match:
        return None
    depth = 1
    cursor = match.end()
    while cursor < len(content) and depth:
        char = content[cursor]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
        cursor += 1
    return content[match.start():cursor] if depth == 0 else None


def validate(label: str, content: str) -> list[str]:
    errors: list[str] = []
    block = extract_block(content)
    if block is None:
        return [f"{label}: 缺少订单/售后 SSE 专用 location"]

    compact_block = re.sub(r"\s+", " ", block)
    for directive, purpose in REQUIRED_DIRECTIVES.items():
        compact_directive = re.sub(r"\s+", " ", directive)
        if compact_directive not in compact_block:
            errors.append(f"{label}: 缺少 {purpose}（{directive}）")

    if re.search(r"location\s+\^~\s+/api/\s*\{", content):
        errors.append(f"{label}: 通用 ^~ /api/ 会阻止 SSE 正则 location 生效")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="验证灵启商城 Nginx SSE 代理配置")
    parser.add_argument("configs", nargs="+", help="Nginx 配置文件；使用 - 可读取 nginx -T 输出")
    args = parser.parse_args()

    errors: list[str] = []
    for path in args.configs:
        try:
            label, content = read_config(path)
            config_errors = validate(label, content)
            errors.extend(config_errors)
            if not config_errors:
                print(f"PASS {label}: SSE 代理配置完整")
        except (OSError, UnicodeError) as exc:
            errors.append(f"{path}: 无法读取配置（{exc}）")

    for error in errors:
        print(f"FAIL {error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
