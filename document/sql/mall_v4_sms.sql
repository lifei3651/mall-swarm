-- =============================================
-- mall-swarm v4 短信验证码表
-- =============================================

DROP TABLE IF EXISTS `ums_sms_code`;
CREATE TABLE `ums_sms_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `code` varchar(10) NOT NULL COMMENT '验证码',
  `biz_type` int(1) DEFAULT 1 COMMENT '业务类型:1=注册,2=登录,3=找回密码,4=修改手机号',
  `status` int(1) DEFAULT 0 COMMENT '状态:0=未使用,1=已使用,2=已过期',
  `ip` varchar(50) DEFAULT NULL COMMENT '请求IP',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_phone_biz` (`phone`, `biz_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码表';
