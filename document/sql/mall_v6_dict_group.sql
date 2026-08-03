-- 字典分组表
DROP TABLE IF EXISTS `sys_dict_group`;
CREATE TABLE `sys_dict_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_code` varchar(100) NOT NULL COMMENT '分组编码',
  `group_name` varchar(100) NOT NULL COMMENT '分组名称',
  `description` varchar(200) DEFAULT NULL,
  `status` int(1) DEFAULT 1,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典分组表';

-- 字典项表
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_code` varchar(100) NOT NULL COMMENT '分组编码',
  `item_code` varchar(100) NOT NULL COMMENT '项编码',
  `item_name` varchar(100) NOT NULL COMMENT '项名称',
  `item_value` varchar(500) DEFAULT NULL COMMENT '项值',
  `sort` int(11) DEFAULT 0,
  `status` int(1) DEFAULT 1,
  `remark` varchar(200) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_item` (`group_code`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';

-- 初始化字典数据
INSERT INTO `sys_dict_group` (`group_code`, `group_name`, `description`) VALUES
('pay_type', '支付方式', '支付方式配置'),
('order_status', '订单状态', '订单状态定义'),
('logistics_company', '物流公司', '物流公司列表'),
('after_sale_type', '售后类型', '售后类型定义'),
('member_level', '会员等级', '会员等级配置'),
('notice_type', '公告类型', '公告类型');

INSERT INTO `sys_dict_item` (`group_code`, `item_code`, `item_name`, `item_value`, `sort`) VALUES
('pay_type', 'alipay', '支付宝', '1', 100),
('pay_type', 'wechat', '微信支付', '2', 90),
('pay_type', 'balance', '余额支付', '3', 80),
('pay_type', 'mixed', '混合支付', '4', 70),
('order_status', 'pending_pay', '待付款', '0', 100),
('order_status', 'pending_ship', '待发货', '1', 90),
('order_status', 'shipped', '已发货', '2', 80),
('order_status', 'completed', '已完成', '3', 70),
('order_status', 'closed', '已关闭', '4', 60),
('order_status', 'invalid', '无效订单', '5', 50),
('order_status', 'after_sale', '售后中', '6', 40),
('logistics_company', 'sf', '顺丰速运', '顺丰速运', 100),
('logistics_company', 'yd', '韵达快递', '韵达快递', 90),
('logistics_company', 'zt', '中通快递', '中通快递', 80),
('logistics_company', 'yt', '圆通速递', '圆通速递', 70),
('logistics_company', 'jd', '京东物流', '京东物流', 60),
('after_sale_type', 'refund_only', '仅退款', '1', 100),
('after_sale_type', 'return_refund', '退货退款', '2', 90),
('after_sale_type', 'exchange', '换货', '3', 80),
('notice_type', 'system', '系统公告', '1', 100),
('notice_type', 'activity', '活动公告', '2', 90),
('notice_type', 'logistics', '物流公告', '3', 80);
