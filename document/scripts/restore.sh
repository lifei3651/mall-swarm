#!/bin/bash
# MySQL 恢复脚本
if [ -z "$1" ]; then
    echo "用法: $0 <备份文件路径>"
    exit 1
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"
DB_NAME="${DB_NAME:-mall}"

gunzip < $1 | mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASSWORD $DB_NAME

echo "恢复完成: $1"
