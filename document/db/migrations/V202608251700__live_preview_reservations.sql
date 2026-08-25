-- 1.0.87 直播预告会员预约记录。
-- 预约只保存会员对公开预告的意向，不启用或发送任何外部短信、App推送或小程序通知。

CREATE TABLE IF NOT EXISTS dms_live_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    live_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1已预约 0已取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_reservation_member (tenant_id, live_room_id, user_id),
    KEY idx_live_reservation_notice (tenant_id, live_room_id, status, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播预告会员预约';
