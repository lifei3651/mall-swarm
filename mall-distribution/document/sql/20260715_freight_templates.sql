-- 商品发货省市区、地区运费模板及订单收货地址快照。

CREATE TABLE IF NOT EXISTS dms_freight_template (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL DEFAULT 1,
  template_name varchar(128) NOT NULL,
  default_mode varchar(32) NOT NULL DEFAULT 'FREE' COMMENT 'FREE/FIXED/UNAVAILABLE',
  default_freight_amount decimal(12,2) NOT NULL DEFAULT 0,
  rules_json json DEFAULT NULL COMMENT '省市区包邮/加运费/不发货特例',
  status tinyint NOT NULL DEFAULT 1,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_template_name (tenant_id,template_name),
  KEY idx_tenant_status (tenant_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中国省市区运费模板';

ALTER TABLE dms_shop_product
  ADD COLUMN delivery_province varchar(64) DEFAULT NULL AFTER delivery_address,
  ADD COLUMN delivery_city varchar(64) DEFAULT NULL AFTER delivery_province,
  ADD COLUMN delivery_district varchar(64) DEFAULT NULL AFTER delivery_city,
  ADD COLUMN freight_template_id bigint DEFAULT NULL AFTER freight_template_name;

ALTER TABLE dms_shop_order
  ADD COLUMN receiver_province varchar(64) DEFAULT NULL AFTER receiver_address,
  ADD COLUMN receiver_city varchar(64) DEFAULT NULL AFTER receiver_province,
  ADD COLUMN receiver_district varchar(64) DEFAULT NULL AFTER receiver_city,
  ADD COLUMN receiver_detail_address varchar(512) DEFAULT NULL AFTER receiver_district;
