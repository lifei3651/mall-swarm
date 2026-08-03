-- 积分规则表
DROP TABLE IF EXISTS `ums_points_rule`;
CREATE TABLE `ums_points_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `rule_type` int(1) NOT NULL COMMENT '规则类型:1=获取,2=消费',
  `action_type` varchar(50) NOT NULL COMMENT '动作类型:ORDER=下单,REVIEW=评价,LOGIN=登录,REGISTER=注册,EXCHANGE=兑换',
  `points` int(11) NOT NULL COMMENT '积分数量',
  `conditions` varchar(500) DEFAULT NULL COMMENT '条件(JSON)',
  `status` int(1) DEFAULT 1,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分规则表';

INSERT INTO `ums_points_rule` (`rule_name`, `rule_type`, `action_type`, `points`, `status`) VALUES
('下单获取积分', 1, 'ORDER', 1, 1),
('评价获取积分', 1, 'REVIEW', 10, 1),
('每日登录', 1, 'LOGIN', 5, 1),
('注册赠送', 1, 'REGISTER', 100, 1),
('积分兑换', 2, 'EXCHANGE', 1, 1);

-- 积分流水表
DROP TABLE IF EXISTS `ums_points_flow`;
CREATE TABLE `ums_points_flow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `member_id` bigint(20) NOT NULL,
  `points` int(11) NOT NULL COMMENT '积分变动(正数获取,负数消费)',
  `balance_after` int(11) DEFAULT NULL COMMENT '变动后积分余额',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型',
  `biz_id` varchar(100) DEFAULT NULL COMMENT '业务ID',
  `description` varchar(200) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- 积分商品表
DROP TABLE IF EXISTS `ums_points_product`;
CREATE TABLE `ums_points_product` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_name` varchar(200) NOT NULL COMMENT '商品名称',
  `product_pic` varchar(500) DEFAULT NULL COMMENT '商品图片',
  `points_price` int(11) NOT NULL COMMENT '积分价格',
  `cash_price` decimal(10,2) DEFAULT 0.00 COMMENT '现金价格(积分+现金)',
  `stock` int(11) DEFAULT 0 COMMENT '库存',
  `exchange_limit` int(11) DEFAULT 0 COMMENT '每人限兑(0=不限)',
  `status` int(1) DEFAULT 1,
  `sort` int(11) DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商品表';

INSERT INTO `ums_points_product` (`product_name`, `points_price`, `stock`, `status`, `sort`) VALUES
('10元优惠券', 100, 999, 1, 100),
('50元优惠券', 500, 999, 1, 90),
('精美礼品', 1000, 50, 1, 80);

-- 商品图片集表
DROP TABLE IF EXISTS `pms_product_gallery`;
CREATE TABLE `pms_product_gallery` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `pic_url` varchar(500) NOT NULL COMMENT '图片URL',
  `pic_type` int(1) DEFAULT 1 COMMENT '类型:1=轮播图,2=详情图,3=视频封面',
  `video_url` varchar(500) DEFAULT NULL COMMENT '视频URL(仅pic_type=3时有效)',
  `sort` int(11) DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片集表';

-- 邀请记录表
DROP TABLE IF EXISTS `ums_invite_record`;
CREATE TABLE `ums_invite_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `inviter_id` bigint(20) NOT NULL COMMENT '邀请人ID',
  `inviter_name` varchar(64) DEFAULT NULL COMMENT '邀请人用户名',
  `invitee_id` bigint(20) NOT NULL COMMENT '被邀请人ID',
  `invitee_name` varchar(64) DEFAULT NULL COMMENT '被邀请人用户名',
  `invite_code` varchar(20) DEFAULT NULL COMMENT '邀请码',
  `reward_points` int(11) DEFAULT 0 COMMENT '奖励积分',
  `reward_amount` decimal(10,2) DEFAULT 0.00 COMMENT '奖励金额',
  `status` int(1) DEFAULT 0 COMMENT '状态:0=待确认,1=已确认,2=已发放',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_inviter_id` (`inviter_id`),
  KEY `idx_invitee_id` (`invitee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请记录表';

-- 邀请规则配置
INSERT INTO `sms_home_config` (`config_key`, `config_value`, `config_desc`, `tenant_id`) VALUES
('invite_reward', '{"enabled":false,"inviterPoints":0,"inviterAmount":0,"inviteePoints":0,"inviteeAmount":0}', '邀请有礼规则:已关闭积分奖励', 1);
