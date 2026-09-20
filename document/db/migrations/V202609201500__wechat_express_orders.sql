-- 微信物流助手快递配送下单记录。
-- 只保存履约所需的账号标识、包裹参数与运单关联，不保存收发件人地址、电话、OpenID、面单正文或访问令牌。

CREATE TABLE IF NOT EXISTS dms_wechat_express_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    express_order_no VARCHAR(128) NOT NULL,
    delivery_id VARCHAR(64) NOT NULL,
    delivery_name VARCHAR(50) NOT NULL,
    biz_id VARCHAR(128) NOT NULL,
    service_type INT NOT NULL,
    service_name VARCHAR(80) NOT NULL,
    shipment_quantity INT NOT NULL,
    package_count INT NOT NULL DEFAULT 1,
    weight DECIMAL(10,3) NOT NULL,
    package_length DECIMAL(10,2) NOT NULL,
    package_width DECIMAL(10,2) NOT NULL,
    package_height DECIMAL(10,2) NOT NULL,
    remark VARCHAR(300) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    waybill_id VARCHAR(64) NULL,
    shipment_id BIGINT NULL,
    error_code VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_express_request (tenant_id, order_id, request_key),
    UNIQUE KEY uk_wechat_express_order_no (tenant_id, express_order_no),
    KEY idx_wechat_express_shipment (tenant_id, shipment_id),
    KEY idx_wechat_express_status (status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信物流助手快递配送订单';
