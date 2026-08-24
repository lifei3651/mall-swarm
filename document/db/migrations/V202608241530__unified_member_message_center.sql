CREATE TABLE IF NOT EXISTS dms_member_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    target_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    target_id BIGINT NULL,
    target_parent_id BIGINT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    read_time DATETIME NULL,
    occurred_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_message_event (tenant_id, member_id, event_key),
    KEY idx_member_message_unread (tenant_id, member_id, is_read, id),
    KEY idx_member_message_category (tenant_id, member_id, category, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员个人站内消息快照（不含商城公告）';

CREATE TABLE IF NOT EXISTS dms_message_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    title_template VARCHAR(128) NOT NULL,
    summary_template VARCHAR(300) NOT NULL,
    content_template VARCHAR(1000) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_template_event (tenant_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息模板；只影响未来消息';

CREATE TABLE IF NOT EXISTS dms_message_channel_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    in_app_enabled TINYINT NOT NULL DEFAULT 1,
    sms_enabled TINYINT NOT NULL DEFAULT 0,
    app_push_enabled TINYINT NOT NULL DEFAULT 0,
    mini_program_enabled TINYINT NOT NULL DEFAULT 0,
    estimated_sms_cost DECIMAL(10,4) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_channel_event (tenant_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息事件渠道开关；外部渠道默认关闭';

CREATE TABLE IF NOT EXISTS dms_message_delivery_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(10,4) NOT NULL DEFAULT 0,
    provider_message_id VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(255) NULL,
    next_retry_time DATETIME NULL,
    sent_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_delivery_channel (tenant_id, message_id, channel),
    KEY idx_message_delivery_status (tenant_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脱敏消息发送任务与结果记录';

INSERT INTO dms_message_template
    (tenant_id, event_type, category, title_template, summary_template, content_template, enabled, version)
SELECT 1, seed.event_type, seed.category, seed.title_template, seed.summary_template, seed.content_template, 1, 1
FROM (
    SELECT 'ORDER_PAID' event_type, 'ORDER_LOGISTICS' category, '订单支付成功' title_template, '订单已完成支付，可查看订单详情。' summary_template, '您的订单已完成支付，后续状态请以订单详情为准。' content_template
    UNION ALL SELECT 'ORDER_CLOSED','ORDER_LOGISTICS','订单已关闭','订单已关闭，可查看订单详情。','您的订单已关闭，具体原因和退款进度请以订单详情为准。'
    UNION ALL SELECT 'ORDER_SHIPPED','ORDER_LOGISTICS','订单已发货','商家已发货，可查看物流信息。','您的订单已发货，物流公司和运单信息请登录后在订单详情查看。'
    UNION ALL SELECT 'ORDER_RECEIVED','ORDER_LOGISTICS','订单已完成','订单已确认收货。','您的订单已确认收货，如需售后请在有效期内从订单详情发起。'
    UNION ALL SELECT 'AFTER_SALE_APPLIED','AFTER_SALE_REFUND','售后申请已提交','售后申请已提交，请留意处理进展。','您的售后申请已提交，申请内容请登录后查看。'
    UNION ALL SELECT 'AFTER_SALE_UPDATED','AFTER_SALE_REFUND','售后状态有更新','售后申请有新的处理进展。','您的售后申请状态已更新，结果及下一步操作请登录后查看。'
    UNION ALL SELECT 'REFUND_RESULT','AFTER_SALE_REFUND','退款结果已更新','退款处理结果已更新。','退款处理结果已更新，到账情况请以原支付渠道或钱包流水为准。'
    UNION ALL SELECT 'WALLET_FLOW','WALLET_FUNDS','钱包有新流水','钱包余额流水已更新。','您的钱包产生一笔新流水，金额和交易对方等敏感信息请登录后查看。'
    UNION ALL SELECT 'WITHDRAW_SUBMITTED','WALLET_FUNDS','提现申请已提交','提现申请已进入审核。','您的提现申请已提交，收款账户和金额请登录后查看。'
    UNION ALL SELECT 'WITHDRAW_AUDITED','WALLET_FUNDS','提现审核已完成','提现申请审核状态已更新。','您的提现申请审核状态已更新，详细结果请登录后查看。'
    UNION ALL SELECT 'WITHDRAW_PAID','WALLET_FUNDS','提现打款状态已更新','提现打款状态已更新。','您的提现打款状态已更新，实际到账请以收款渠道为准。'
    UNION ALL SELECT 'LOGIN_PASSWORD_CHANGED','ACCOUNT_SECURITY','登录密码已修改','账号安全设置发生变化。','您的登录密码已修改；如非本人操作，请立即联系平台。'
    UNION ALL SELECT 'PAY_PASSWORD_CHANGED','ACCOUNT_SECURITY','支付密码已更新','资金安全设置发生变化。','您的支付密码已更新；验证码和密码不会出现在消息正文或发送记录中。'
    UNION ALL SELECT 'PHONE_CHANGED','ACCOUNT_SECURITY','登录手机号已更新','账号安全设置发生变化。','您的登录手机号已更新；完整号码不会出现在消息正文中。'
    UNION ALL SELECT 'SERVICE_NOTICE','SERVICE','服务通知','您有一条新的服务通知。','您有一条新的服务通知，请登录后查看。'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM dms_message_template existing
    WHERE existing.tenant_id = 1 AND existing.event_type = seed.event_type
);

INSERT INTO dms_message_channel_config
    (tenant_id, event_type, in_app_enabled, sms_enabled, app_push_enabled, mini_program_enabled, estimated_sms_cost)
SELECT 1, template.event_type, 1, 0, 0, 0, 0
FROM dms_message_template template
WHERE template.tenant_id = 1
  AND NOT EXISTS (
      SELECT 1 FROM dms_message_channel_config existing
      WHERE existing.tenant_id = 1 AND existing.event_type = template.event_type
  );
