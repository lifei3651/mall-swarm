-- 商品中心：媒体、发货、运费和售后配置。PV 是否显示由商品发布全局开关控制，默认开启。
ALTER TABLE dms_shop_product
  ADD COLUMN gallery_urls JSON DEFAULT NULL COMMENT '商品轮播图' AFTER cover_url,
  ADD COLUMN detail_images JSON DEFAULT NULL COMMENT '详情图片' AFTER detail,
  ADD COLUMN delivery_address VARCHAR(255) DEFAULT NULL COMMENT '发货地' AFTER detail_images,
  ADD COLUMN freight_type TINYINT NOT NULL DEFAULT 0 COMMENT '0包邮 1固定运费 2满额包邮 3运费模板' AFTER delivery_address,
  ADD COLUMN freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '固定运费' AFTER freight_type,
  ADD COLUMN free_shipping_amount DECIMAL(12,2) DEFAULT NULL COMMENT '满额包邮门槛' AFTER freight_amount,
  ADD COLUMN freight_template_name VARCHAR(128) DEFAULT NULL COMMENT '运费模板名称' AFTER free_shipping_amount;

ALTER TABLE dms_shop_sku
  ADD COLUMN image_url VARCHAR(512) DEFAULT NULL COMMENT 'SKU图片' AFTER attrs_json;

ALTER TABLE dms_shop_product
  ADD COLUMN delivery_time VARCHAR(64) DEFAULT NULL COMMENT '承诺发货时效' AFTER freight_template_name,
  ADD COLUMN after_sale_policy VARCHAR(1000) DEFAULT NULL COMMENT '售后政策' AFTER delivery_time,
  ADD COLUMN service_tags JSON DEFAULT NULL COMMENT '服务标签数组' AFTER after_sale_policy;
