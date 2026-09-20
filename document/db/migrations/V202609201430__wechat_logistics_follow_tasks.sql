-- 发货后自动登记微信物流消息。
-- 每个商城订单包裹只保留一条任务；不回填历史已发货订单，避免对旧订单补发过期物流提醒。

CREATE TABLE IF NOT EXISTS dms_wechat_logistics_follow_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    shipment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NULL,
    lease_owner VARCHAR(96) NULL,
    lease_until DATETIME NULL,
    payload_digest CHAR(64) NULL,
    error_code VARCHAR(64) NULL,
    registered_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_logistics_shipment (tenant_id, order_id, shipment_id),
    KEY idx_wechat_logistics_due (status, next_retry_time, lease_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信物流消息运单登记任务';
