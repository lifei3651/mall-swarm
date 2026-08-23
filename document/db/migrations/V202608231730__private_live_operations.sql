-- 私域直播运营闭环：主播授权、开停播控制、互动会话、评论与订单转化归因。
-- 云直播鉴权密钥只允许通过服务器环境变量注入，本迁移不保存任何推流密钥。

SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='anchor_id');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN anchor_id BIGINT NULL COMMENT '平台授权主播ID' AFTER anchor_name",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='live_type');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN live_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCT' COMMENT 'PRODUCT厂家商品 PLATFORM平台讲解 FACTORY工厂常态' AFTER anchor_id",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='provider_code');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN provider_code VARCHAR(24) NOT NULL DEFAULT 'EXTERNAL' COMMENT 'EXTERNAL外部地址 TENCENT腾讯云' AFTER live_type",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='stream_name');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN stream_name VARCHAR(96) NULL COMMENT '公开流标识，不含推流鉴权信息' AFTER provider_code",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='comment_enabled');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN comment_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许评论' AFTER watch_url",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='share_enabled');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN share_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许分享' AFTER comment_enabled",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='actual_start_time');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN actual_start_time DATETIME NULL COMMENT '实际开播时间' AFTER scheduled_end_time",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='actual_end_time');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN actual_end_time DATETIME NULL COMMENT '实际停播时间' AFTER actual_start_time",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_live_room' AND COLUMN_NAME='stop_reason');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_live_room ADD COLUMN stop_reason VARCHAR(200) NULL COMMENT '平台停播原因' AFTER actual_end_time",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_live_anchor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    member_user_id BIGINT NOT NULL COMMENT '复用商城会员登录账号',
    display_name VARCHAR(60) NOT NULL,
    anchor_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCT' COMMENT 'PRODUCT厂家 PLATFORM平台 FACTORY工厂',
    company_name VARCHAR(120) DEFAULT NULL,
    bio VARCHAR(300) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1可开播 2暂停 3已收回',
    last_live_time DATETIME DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_anchor_member (tenant_id, member_user_id),
    KEY idx_live_anchor_status (tenant_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台定向授权直播账号';

CREATE TABLE IF NOT EXISTS dms_live_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    live_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(60) NOT NULL,
    content VARCHAR(300) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1公开 2平台隐藏',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_live_comment_room (tenant_id, live_room_id, status, id),
    KEY idx_live_comment_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私域直播评论';

CREATE TABLE IF NOT EXISTS dms_live_view_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    live_room_id BIGINT NOT NULL,
    visitor_id CHAR(36) NOT NULL COMMENT '客户端随机访客标识，不保存设备指纹',
    user_id BIGINT DEFAULT NULL,
    enter_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_seconds INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_view_visitor (tenant_id, live_room_id, visitor_id),
    KEY idx_live_view_active (tenant_id, live_room_id, last_seen_time),
    KEY idx_live_view_user (tenant_id, user_id, last_seen_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播观看会话与停留时长';

CREATE TABLE IF NOT EXISTS dms_live_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    live_room_id BIGINT NOT NULL,
    visitor_id CHAR(36) NOT NULL,
    user_id BIGINT DEFAULT NULL,
    event_type VARCHAR(24) NOT NULL COMMENT 'ENTER LEAVE SHARE PRODUCT_CLICK COMMENT',
    product_id BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_live_event_room (tenant_id, live_room_id, event_type, create_time),
    KEY idx_live_event_attribution (tenant_id, user_id, product_id, event_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播互动与转化归因事件';

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='source_live_room_id');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_order ADD COLUMN source_live_room_id BIGINT NULL COMMENT '最近24小时直播商品点击归因' AFTER business_source_id",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND INDEX_NAME='idx_shop_order_live_conversion');
SET @sql = IF(@exists=0,
              'CREATE INDEX idx_shop_order_live_conversion ON dms_shop_order (tenant_id, source_live_room_id, pay_time, status)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
