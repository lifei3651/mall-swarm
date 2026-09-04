-- 微信小程序订阅消息单次授权与微信支付发货信息同步。
-- 只新增任务/授权表并开放事件级小程序渠道标识；运行总开关和模板编号仍默认关闭。

CREATE TABLE IF NOT EXISTS dms_mini_program_subscription_grant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    template_id_hash CHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    reserved_task_id BIGINT NULL,
    authorized_time DATETIME NOT NULL,
    reserved_time DATETIME NULL,
    consumed_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mini_subscribe_request (tenant_id, member_id, template_id_hash, client_request_id),
    UNIQUE KEY uk_mini_subscribe_task (tenant_id, reserved_task_id),
    KEY idx_mini_subscribe_available (tenant_id, member_id, template_id_hash, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信小程序一次性订阅授权；不保存OpenID或模板原文';

CREATE TABLE IF NOT EXISTS dms_wechat_shipping_sync_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    payment_order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    revision INT NOT NULL DEFAULT 1,
    synced_revision INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NULL,
    lease_owner VARCHAR(96) NULL,
    lease_until DATETIME NULL,
    payload_digest CHAR(64) NULL,
    error_code VARCHAR(64) NULL,
    synced_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_shipping_payment (tenant_id, payment_order_no),
    KEY idx_wechat_shipping_due (status, next_retry_time, lease_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信支付订单发货信息同步任务';

UPDATE dms_message_channel_config
SET mini_program_enabled = 1, update_time = CURRENT_TIMESTAMP
WHERE event_type IN ('ORDER_SHIPPED','AFTER_SALE_UPDATED','REFUND_RESULT','WITHDRAW_PAID')
  AND mini_program_enabled = 0;
