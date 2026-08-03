#!/bin/bash
# MySQL 自动备份脚本
BACKUP_DIR="/data/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"
DB_NAME="${DB_NAME:-mall}"

mkdir -p $BACKUP_DIR

mysqldump -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASSWORD $DB_NAME | gzip > $BACKUP_DIR/mall_$DATE.sql.gz

# 保留最近30天的备份
find $BACKUP_DIR -name "mall_*.sql.gz" -mtime +30 -delete

echo "备份完成: mall_$DATE.sql.gz"
