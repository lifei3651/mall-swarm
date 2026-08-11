CREATE TABLE IF NOT EXISTS dms_tenant_config_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '商城客户ID',
    version_no VARCHAR(64) NOT NULL COMMENT '版本号',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型',
    tenant_snapshot LONGTEXT NOT NULL COMMENT '商城资料快照',
    display_snapshot LONGTEXT NOT NULL COMMENT '商城视觉与展示配置快照',
    operator_id BIGINT NOT NULL DEFAULT 0 COMMENT '操作管理员ID',
    operator_name VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '操作管理员账号',
    source_version_id BIGINT DEFAULT NULL COMMENT '恢复操作的来源版本ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config_version_no (tenant_id, version_no),
    KEY idx_tenant_config_version_time (tenant_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城客户配置历史版本';

SET @operation_log_index_exists := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dms_operation_log'
      AND INDEX_NAME = 'idx_operation_log_create_time'
);
SET @operation_log_index_sql := IF(
    @operation_log_index_exists = 0,
    'ALTER TABLE dms_operation_log ADD INDEX idx_operation_log_create_time (create_time, id)',
    'SELECT 1'
);
PREPARE operation_log_index_stmt FROM @operation_log_index_sql;
EXECUTE operation_log_index_stmt;
DEALLOCATE PREPARE operation_log_index_stmt;
