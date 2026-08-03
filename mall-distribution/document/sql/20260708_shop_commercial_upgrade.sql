-- 商城商业版增量升级脚本
-- 适用：已经部署过旧版 mall-distribution 数据库，需要补会员、地址、SKU、物流、售后能力。
-- 注意：ALTER TABLE ADD COLUMN 请只执行一次；全新数据库请优先执行 distribution.sql。

CREATE TABLE IF NOT EXISTS `dms_admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希',
  `salt` varchar(64) NOT NULL COMMENT '密码盐',
  `pay_password_hash` varchar(128) DEFAULT NULL COMMENT '独立支付密码哈希（BCrypt）',
  `pay_password_failed_count` int NOT NULL DEFAULT 0 COMMENT '连续支付密码错误次数',
  `pay_password_lock_time` datetime DEFAULT NULL COMMENT '支付密码锁定时间',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `role_code` varchar(64) NOT NULL DEFAULT 'OPERATOR' COMMENT '角色编码',
  `permissions` text COMMENT '权限码，逗号分隔，* 表示全部',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员账号表';

CREATE TABLE IF NOT EXISTS `dms_admin_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `token` varchar(128) NOT NULL COMMENT '登录令牌',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0失效 1有效',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员会话表';

INSERT IGNORE INTO `dms_admin_user`
  (`id`, `username`, `password_hash`, `salt`, `nickname`, `role_code`, `permissions`, `status`)
VALUES
  (1, 'admin', '9caec3496b444e62944109574e4a98a3a1cde7f063c9e1c6c5700576f3ab773f', 'admin-default-salt', '超级管理员', 'SUPER_ADMIN', '*', 1);

CREATE TABLE IF NOT EXISTS `dms_shop_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `username` varchar(64) DEFAULT NULL COMMENT '用户名',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希',
  `salt` varchar(64) NOT NULL COMMENT '密码盐',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '头像',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城会员表';

CREATE TABLE IF NOT EXISTS `dms_tenant_display_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `show_pv` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示PV',
  `show_team_performance` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示团队业绩',
  `show_bonus_source` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示奖金来源',
  `show_bonus_flow` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示奖金流向',
  `show_profit` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示利润',
  `show_rank` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示排名/职级',
  `show_binary_area` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示双轨/大小区',
  `show_retail_module` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示新零售模块',
  `show_store_module` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示门店模块',
  `show_company_share` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示公司分账',
  `extra_config_json` json DEFAULT NULL COMMENT '扩展展示配置',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端展示开关配置表';

CREATE TABLE IF NOT EXISTS `dms_shop_member_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `token` varchar(128) NOT NULL COMMENT '登录令牌',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-失效 1-有效',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城会员会话表';

CREATE TABLE IF NOT EXISTS `dms_shop_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货手机号',
  `province` varchar(64) DEFAULT NULL COMMENT '省',
  `city` varchar(64) DEFAULT NULL COMMENT '市',
  `district` varchar(64) DEFAULT NULL COMMENT '区县',
  `detail_address` varchar(512) NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-删除 1-正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城会员收货地址表';

CREATE TABLE IF NOT EXISTS `dms_shop_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `category_name` varchar(64) NOT NULL COMMENT '分类名称',
  `icon_url` varchar(512) DEFAULT NULL COMMENT '分类图标',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城分类表';

CREATE TABLE IF NOT EXISTS `dms_shop_banner` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮播ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `image_url` varchar(512) NOT NULL COMMENT '图片地址',
  `link_type` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '跳转类型：NONE/PRODUCT/CATEGORY/URL',
  `link_value` varchar(256) DEFAULT NULL COMMENT '跳转值',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城首页轮播表';

CREATE TABLE IF NOT EXISTS `dms_shop_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` varchar(1000) DEFAULT NULL COMMENT '公告内容',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城公告表';

INSERT IGNORE INTO `dms_shop_category`
(`id`, `tenant_id`, `category_name`, `icon_url`, `sort_order`, `status`, `remark`)
VALUES
(1, 1, '护理套装', NULL, 100, 1, '首页推荐分类'),
(2, 1, '健康生活', NULL, 90, 1, '复购商品分类'),
(3, 1, '尊享套装', NULL, 80, 1, '高客单分类'),
(4, 1, '复购专区', NULL, 70, 1, '轻量复购分类');

INSERT IGNORE INTO `dms_shop_banner`
(`id`, `tenant_id`, `title`, `image_url`, `link_type`, `link_value`, `sort_order`, `status`, `remark`)
VALUES
(1, 1, '商城精选套装', 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=1400&q=80', 'PRODUCT', '1', 100, 1, '首页主轮播'),
(2, 1, '家庭复购活动', 'https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=1400&q=80', 'CATEGORY', '健康生活', 90, 1, '分类活动轮播');

INSERT IGNORE INTO `dms_shop_notice`
(`id`, `tenant_id`, `title`, `content`, `sort_order`, `status`)
VALUES
(1, 1, '内部测试商城已开启', '当前为内部全流程测试环境，正式支付通道完成商户配置后启用；生产环境不开放模拟支付。', 100, 1);

CREATE TABLE IF NOT EXISTS `dms_shop_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_no` varchar(64) NOT NULL COMMENT 'SKU编号',
  `sku_name` varchar(128) NOT NULL COMMENT 'SKU名称',
  `attrs_json` json DEFAULT NULL COMMENT '规格属性JSON',
  `sale_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '销售价',
  `market_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '划线价',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件成本',
  `pv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件PV',
  `bv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件BV',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `sales_count` int NOT NULL DEFAULT 0 COMMENT '销量',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_no` (`sku_no`),
  KEY `idx_product_status` (`product_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城SKU表';

CREATE TABLE IF NOT EXISTS `dms_shop_after_sale` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '售后ID',
  `after_sale_no` varchar(64) NOT NULL COMMENT '售后单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `apply_type` tinyint NOT NULL DEFAULT 1 COMMENT '申请类型：1-退款 2-退货退款',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '退款金额',
  `reason` varchar(512) DEFAULT NULL COMMENT '原因',
  `proof_images` text DEFAULT NULL COMMENT '凭证图片，逗号分隔',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-通过 2-拒绝',
  `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注',
  `audit_user_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `audit_user_name` varchar(64) DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_no` (`after_sale_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城售后表';

ALTER TABLE `dms_shop_order`
  ADD COLUMN `delivery_company` varchar(64) DEFAULT NULL COMMENT '物流公司' AFTER `pay_time`,
  ADD COLUMN `delivery_no` varchar(64) DEFAULT NULL COMMENT '物流单号' AFTER `delivery_company`,
  ADD COLUMN `delivery_time` datetime DEFAULT NULL COMMENT '发货时间' AFTER `delivery_no`,
  ADD COLUMN `receive_time` datetime DEFAULT NULL COMMENT '确认收货时间' AFTER `delivery_time`,
  ADD COLUMN `cancel_time` datetime DEFAULT NULL COMMENT '取消时间' AFTER `receive_time`,
  ADD COLUMN `close_time` datetime DEFAULT NULL COMMENT '关闭时间' AFTER `cancel_time`;

ALTER TABLE `dms_shop_order_item`
  ADD COLUMN `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID' AFTER `product_id`,
  ADD COLUMN `sku_name` varchar(128) DEFAULT NULL COMMENT 'SKU名称' AFTER `product_name`,
  ADD COLUMN `sku_attrs` json DEFAULT NULL COMMENT '规格属性快照' AFTER `sku_name`;

INSERT IGNORE INTO `dms_shop_sku`
(`product_id`, `sku_no`, `sku_name`, `attrs_json`, `sale_price`, `market_price`, `cost_amount`, `pv_value`, `bv_value`, `stock`, `sales_count`, `status`)
SELECT `id`, CONCAT(`product_no`, '-DEFAULT'), '默认规格', JSON_OBJECT('规格', '默认规格'),
       `sale_price`, `market_price`, `cost_amount`, `pv_value`, `bv_value`, `stock`, 0, `status`
FROM `dms_shop_product`;

INSERT IGNORE INTO `dms_tenant_display_config`
(`tenant_id`, `show_pv`, `show_team_performance`, `show_bonus_source`, `show_bonus_flow`, `show_profit`, `show_rank`,
 `show_binary_area`, `show_retail_module`, `show_store_module`, `show_company_share`, `extra_config_json`)
VALUES
(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL);
