#!/usr/bin/env bash
set -euo pipefail

# 服务启动后导出当前代码实时生成的 OpenAPI 文档。
api_url="${1:-http://127.0.0.1:8086/v3/api-docs}"
output_file="${2:-document/api/generated/openapi.json}"
output_dir="$(dirname "$output_file")"
temporary_file="$(mktemp)"
trap 'rm -f "$temporary_file"' EXIT

mkdir -p "$output_dir"
curl --fail --silent --show-error "$api_url" -o "$temporary_file"
if ! grep -q '"openapi"' "$temporary_file"; then
  echo "接口文档响应格式不正确：$api_url" >&2
  exit 1
fi
mv "$temporary_file" "$output_file"
echo "接口文档已生成：$output_file"
