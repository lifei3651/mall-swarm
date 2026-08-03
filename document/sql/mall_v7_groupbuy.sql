-- 拼团活动表
DROP TABLE IF EXISTS `sms_group_buy`;
CREATE TABLE `sms_group_buy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '活动标题',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `product_name` varchar(200) DEFAULT NULL COMMENT '商品名称',
  `product_pic` varchar(500) DEFAULT NULL COMMENT '商品图片',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `group_price` decimal(10,2) NOT NULL COMMENT '拼团价',
  `group_size` int(11) DEFAULT 2 COMMENT '成团人数',
  `limit_per_user` int(11) DEFAULT 1 COMMENT '每人限购',
  `total_stock` int(11) DEFAULT 0 COMMENT '总库存',
  `sold_count` int(11) DEFAULT 0 COMMENT '已售数量',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `timeout_minutes` int(11) DEFAULT 30 COMMENT '成团超时时间(分钟)',
  `status` int(1) DEFAULT 0 COMMENT '状态:0=未开始,1=进行中,2=已结束',
  `sort` int(11) DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动表';

-- 拼团订单表
DROP TABLE IF EXISTS `sms_group_order`;
CREATE TABLE `sms_group_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_buy_id` bigint(20) NOT NULL COMMENT '拼团活动ID',
  `group_no` varchar(64) NOT NULL COMMENT '团号',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `order_sn` varchar(64) DEFAULT NULL COMMENT '关联订单号',
  `member_id` bigint(20) NOT NULL COMMENT '会员ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '会员名称',
  `is_leader` int(1) DEFAULT 0 COMMENT '是否团长:0=否,1=是',
  `status` int(1) DEFAULT 0 COMMENT '状态:0=待成团,1=已成团,2=拼团失败,3=已取消',
  `expire_time` datetime COMMENT '过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团订单表';
