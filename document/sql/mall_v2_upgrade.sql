-- =============================================
-- mall-swarm v2 升级脚本
-- 功能：公告管理、商品标签、订单增强
-- =============================================

-- ----------------------------
-- 1. 公告管理表
-- ----------------------------
DROP TABLE IF EXISTS `sms_notice`;
CREATE TABLE `sms_notice` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '公告标题',
  `content` text COMMENT '公告内容',
  `notice_type` int(1) DEFAULT 1 COMMENT '类型:1=系统公告,2=活动公告,3=物流公告',
  `status` int(1) DEFAULT 1 COMMENT '状态:0=下线,1=上线',
  `sort` int(11) DEFAULT 0 COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城公告表';

-- 插入示例数据
INSERT INTO `sms_notice` (`title`, `content`, `notice_type`, `status`, `sort`) VALUES
('欢迎光临灵启商城', '欢迎光临灵启商城，新品上架，优惠多多！', 1, 1, 100),
('物流时效说明', '受天气影响，部分地区物流可能延迟，敬请谅解。', 3, 1, 50),
('双十一活动预告', '双十一全场满减活动即将开启，敬请期待！', 2, 1, 80);

-- ----------------------------
-- 2. 商品标签表
-- ----------------------------
DROP TABLE IF EXISTS `pms_product_tag`;
CREATE TABLE `pms_product_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '标签名称',
  `icon` varchar(500) DEFAULT NULL COMMENT '标签图标URL',
  `color` varchar(20) DEFAULT NULL COMMENT '标签颜色(如#FF0000)',
  `sort` int(11) DEFAULT 0 COMMENT '排序',
  `status` int(1) DEFAULT 1 COMMENT '状态:0=禁用,1=启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品标签表';

-- 插入示例数据
INSERT INTO `pms_product_tag` (`name`, `color`, `sort`, `status`) VALUES
('新品', '#FF4D4F', 100, 1),
('热卖', '#FF7A45', 90, 1),
('限时特价', '#FAAD14', 80, 1),
('包邮', '#52C41A', 70, 1),
('精选', '#1890FF', 60, 1);

-- ----------------------------
-- 3. 商品标签关联表
-- ----------------------------
DROP TABLE IF EXISTS `pms_product_tag_relation`;
CREATE TABLE `pms_product_tag_relation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_tag` (`product_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品标签关联表';

-- ----------------------------
-- 4. 订单表增加字段（如果不存在）
-- ----------------------------
-- 注意：oms_order 已有 note、discount_amount、freight_amount、delivery_company、delivery_sn 字段
-- 无需新增字段，仅需增强后端逻辑

-- ----------------------------
-- 5. 首页配置表（可选，用于后台可视化配置首页）
-- ----------------------------
DROP TABLE IF EXISTS `sms_home_config`;
CREATE TABLE `sms_home_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值(JSON)',
  `config_desc` varchar(200) COMMENT '配置说明',
  `tenant_id` bigint(20) DEFAULT 1 COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_tenant` (`config_key`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页配置表';

INSERT INTO `sms_home_config` (`config_key`, `config_value`, `config_desc`, `tenant_id`) VALUES
('home_carousel', '{"enabled":true,"maxCount":8}', '首页轮播图配置', 1),
('home_notice', '{"enabled":true,"maxCount":5,"autoScroll":true}', '首页公告配置', 1),
('home_categories', '{"enabled":true,"showLevel":1}', '首页分类展示配置', 1),
('home_recommend', '{"enabled":true,"title":"为你推荐","maxCount":8}', '首页推荐商品配置', 1),
('home_new_product', '{"enabled":true,"title":"新品上架","maxCount":4}', '首页新品配置', 1),
('home_hot_product', '{"enabled":true,"title":"人气好物","maxCount":4}', '首页热销配置', 1);

-- 大额支付验证码开关配置
INSERT IGNORE INTO `sms_home_config` (`config_key`, `config_value`, `config_desc`, `tenant_id`) VALUES
('payment_sms_verify', '{"enabled":false,"threshold":500,"message":"订单金额较大，请验证手机号"}', '大额支付短信验证开关:enabled=是否开启,threshold=触发金额(元)', 1);
