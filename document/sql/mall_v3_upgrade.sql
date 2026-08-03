-- =============================================
-- mall-swarm v3 升级脚本
-- 功能：会员运营、售后增强、财务导出、系统安全、首页配置、物流管理
-- 前置依赖: mall.sql, mall_v2_upgrade.sql
-- 执行顺序: 在 mall_v2_upgrade.sql 之后执行
-- =============================================

-- ----------------------------
-- 1. 会员标签表增加字段（不删除原表）
-- ----------------------------
-- mall.sql 已有 ums_member_tag 表（含 id, name, finish_order_count, finish_order_amount）
-- 此处增加 color, sort, status, create_time 字段
ALTER TABLE `ums_member_tag` ADD COLUMN IF NOT EXISTS `color` varchar(20) DEFAULT NULL COMMENT '标签颜色';
ALTER TABLE `ums_member_tag` ADD COLUMN IF NOT EXISTS `sort` int(11) DEFAULT 0 COMMENT '排序';
ALTER TABLE `ums_member_tag` ADD COLUMN IF NOT EXISTS `status` int(1) DEFAULT 1 COMMENT '状态:0=禁用,1=启用';
ALTER TABLE `ums_member_tag` ADD COLUMN IF NOT EXISTS `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ----------------------------
-- 2. 会员标签关联表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ums_member_tag_relation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `member_id` bigint(20) NOT NULL COMMENT '会员ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tag` (`member_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='会员标签关联表';

-- ----------------------------
-- 3. 会员等级表增加字段（不删除原表）
-- ----------------------------
-- mall.sql 已有 ums_member_level 表
-- 此处增加 discount_rate, status, sort 字段
ALTER TABLE `ums_member_level` ADD COLUMN IF NOT EXISTS `discount_rate` decimal(4,2) DEFAULT 1.00 COMMENT '折扣率(0.00-1.00)';
ALTER TABLE `ums_member_level` ADD COLUMN IF NOT EXISTS `status` int(1) DEFAULT 1 COMMENT '状态:0=禁用,1=启用';
ALTER TABLE `ums_member_level` ADD COLUMN IF NOT EXISTS `sort` int(11) DEFAULT 0 COMMENT '排序';

-- ----------------------------
-- 4. 会员表增加字段
-- ----------------------------
ALTER TABLE `ums_member` ADD COLUMN IF NOT EXISTS `balance` decimal(10,2) DEFAULT 0.00 COMMENT '账户余额';
ALTER TABLE `ums_member` ADD COLUMN IF NOT EXISTS `member_status` int(1) DEFAULT 1 COMMENT '状态:0=禁用,1=正常,2=黑名单';
ALTER TABLE `ums_member` ADD COLUMN IF NOT EXISTS `blacklisted` int(1) DEFAULT 0 COMMENT '是否黑名单:0=否,1=是';

-- ----------------------------
-- 5. 登录日志表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ums_login_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(64) DEFAULT NULL COMMENT '用户名',
  `login_type` int(1) DEFAULT 1 COMMENT '登录类型:1=后台管理员,2=前台会员',
  `login_ip` varchar(50) DEFAULT NULL COMMENT '登录IP',
  `login_location` varchar(200) DEFAULT NULL COMMENT '登录地点',
  `browser` varchar(50) DEFAULT NULL COMMENT '浏览器',
  `os` varchar(50) DEFAULT NULL COMMENT '操作系统',
  `status` int(1) DEFAULT 1 COMMENT '状态:0=失败,1=成功',
  `message` varchar(200) DEFAULT NULL COMMENT '提示消息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='登录日志表';

-- ----------------------------
-- 6. 角色模板表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ums_role_template` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '模板名称',
  `description` varchar(200) DEFAULT NULL COMMENT '模板描述',
  `resource_ids` text COMMENT '资源ID列表(JSON数组)',
  `menu_ids` text COMMENT '菜单ID列表(JSON数组)',
  `status` int(1) DEFAULT 1 COMMENT '状态:0=禁用,1=启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='角色模板表';

INSERT IGNORE INTO `ums_role_template` (`id`, `name`, `description`, `status`) VALUES
(1, '超级管理员', '拥有所有权限', 1),
(2, '商品管理员', '管理商品、分类、品牌', 1),
(3, '订单管理员', '管理订单、发货、售后', 1),
(4, '财务管理员', '查看财务数据、导出报表', 1),
(5, '客服专员', '处理售后、查看订单', 1);

-- ----------------------------
-- 7. 售后类型表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oms_return_type` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '类型名称',
  `type` int(1) NOT NULL COMMENT '类型:1=仅退款,2=退货退款,3=换货',
  `description` varchar(200) DEFAULT NULL COMMENT '说明',
  `status` int(1) DEFAULT 1 COMMENT '状态',
  `sort` int(11) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='售后类型表';

INSERT IGNORE INTO `oms_return_type` (`id`, `name`, `type`, `description`, `sort`) VALUES
(1, '仅退款', 1, '未发货或已收货但不退货', 100),
(2, '退货退款', 2, '已收货，退回商品并退款', 90),
(3, '换货', 3, '已收货，更换商品', 80);

-- ----------------------------
-- 8. 售后凭证图片表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oms_return_proof` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `return_apply_id` bigint(20) NOT NULL COMMENT '售后申请ID',
  `pic_url` varchar(500) NOT NULL COMMENT '图片URL',
  `sort` int(11) DEFAULT 0 COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_return_apply_id` (`return_apply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='售后凭证图片表';

-- ----------------------------
-- 9. 售后申请表增加字段
-- ----------------------------
ALTER TABLE `oms_order_return_apply` ADD COLUMN IF NOT EXISTS `return_type` int(1) DEFAULT 2 COMMENT '售后类型:1=仅退款,2=退货退款,3=换货';
ALTER TABLE `oms_order_return_apply` ADD COLUMN IF NOT EXISTS `return_logistics_company` varchar(64) DEFAULT NULL COMMENT '退货物流公司';
ALTER TABLE `oms_order_return_apply` ADD COLUMN IF NOT EXISTS `return_logistics_sn` varchar(64) DEFAULT NULL COMMENT '退货物流单号';

-- ----------------------------
-- 10. 财务流水表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oms_finance_flow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `flow_no` varchar(64) NOT NULL COMMENT '流水号',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `order_sn` varchar(64) DEFAULT NULL COMMENT '关联订单号',
  `flow_type` int(1) NOT NULL COMMENT '流水类型:1=收入,2=支出,3=退款,4=佣金',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `balance_after` decimal(10,2) DEFAULT NULL COMMENT '变动后余额',
  `member_id` bigint(20) DEFAULT NULL COMMENT '会员ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '会员名称',
  `description` varchar(200) DEFAULT NULL COMMENT '说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_no` (`flow_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='财务流水表';

-- ----------------------------
-- 11. 物流轨迹表（新建）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oms_logistics_trace` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_sn` varchar(64) DEFAULT NULL COMMENT '订单号',
  `delivery_company` varchar(64) DEFAULT NULL COMMENT '物流公司',
  `delivery_sn` varchar(64) DEFAULT NULL COMMENT '物流单号',
  `trace_status` varchar(50) DEFAULT NULL COMMENT '物流状态',
  `trace_content` varchar(500) DEFAULT NULL COMMENT '物流内容',
  `trace_time` datetime DEFAULT NULL COMMENT '物流时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_delivery_sn` (`delivery_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4mb4 COMMENT='物流轨迹表';

-- ----------------------------
-- 12. 首页配置表数据（依赖 mall_v2_upgrade.sql）
-- ----------------------------
INSERT IGNORE INTO `sms_home_config` (`config_key`, `config_value`, `config_desc`, `tenant_id`) VALUES
('home_flash_sale', '{"enabled":false,"title":"限时秒杀"}', '首页秒杀配置', 1),
('home_brand', '{"enabled":true,"title":"品牌推荐","maxCount":6}', '首页品牌推荐配置', 1),
('home_subject', '{"enabled":true,"title":"精选专题","maxCount":4}', '首页专题配置', 1);
