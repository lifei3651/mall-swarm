-- 直播广场基座与新品首次上架时间。
-- 直播流由客户选择的云服务商提供；本表只保存公开观看地址，不保存推流密钥。

SET @schema_name = DATABASE();
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='first_publish_time');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_product ADD COLUMN first_publish_time DATETIME NULL COMMENT '首次正式上架时间（新品排序口径）' AFTER status",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE dms_shop_product
SET first_publish_time = COALESCE(create_time, CURRENT_TIMESTAMP)
WHERE status = 1 AND first_publish_time IS NULL;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND INDEX_NAME='idx_shop_product_new_arrival');
SET @sql = IF(@exists=0,
              'CREATE INDEX idx_shop_product_new_arrival ON dms_shop_product (tenant_id, status, normal_sale_enabled, first_publish_time, id)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_live_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    title VARCHAR(80) NOT NULL,
    subtitle VARCHAR(160) DEFAULT NULL,
    cover_url VARCHAR(2048) NOT NULL,
    anchor_name VARCHAR(60) DEFAULT NULL,
    watch_url VARCHAR(2048) DEFAULT NULL COMMENT '公开观看或回放地址；禁止保存推流地址与密钥',
    scheduled_start_time DATETIME NOT NULL,
    scheduled_end_time DATETIME DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1预告 2直播中 3已结束 4停用',
    viewer_count INT NOT NULL DEFAULT 0,
    heat_count INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_live_room_tenant_state (tenant_id, status, scheduled_start_time, sort_order),
    KEY idx_live_room_public_order (tenant_id, status, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播间公开展示基座';

CREATE TABLE IF NOT EXISTS dms_live_room_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    live_room_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_room_product (tenant_id, live_room_id, product_id),
    KEY idx_live_product_product (tenant_id, product_id, live_room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播间关联商品';
