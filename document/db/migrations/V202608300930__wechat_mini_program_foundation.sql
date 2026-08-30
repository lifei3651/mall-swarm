-- 微信小程序公共身份基座：每个客户使用自己的 AppID；OpenID/UnionID 由应用层加密后落库。
CREATE TABLE IF NOT EXISTS `dms_wechat_mini_program_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `member_id` bigint NOT NULL COMMENT '商城会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `app_id_hash` char(64) NOT NULL COMMENT '客户小程序AppID哈希',
  `open_id_hash` char(64) NOT NULL COMMENT 'AppID与OpenID组合哈希',
  `union_id_hash` char(64) DEFAULT NULL COMMENT 'UnionID哈希',
  `open_id` varchar(512) NOT NULL COMMENT 'OpenID应用层密文',
  `union_id` varchar(512) DEFAULT NULL COMMENT 'UnionID应用层密文',
  `privacy_consent_version` varchar(64) NOT NULL COMMENT '用户同意的隐私政策版本',
  `privacy_consent_time` datetime NOT NULL COMMENT '隐私政策同意时间',
  `phone_authorized_time` datetime DEFAULT NULL COMMENT '微信手机号授权时间',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-解绑/停用 1-正常',
  `last_login_time` datetime NOT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wx_mini_tenant_app_open` (`tenant_id`, `app_id_hash`, `open_id_hash`),
  UNIQUE KEY `uk_wx_mini_tenant_app_member` (`tenant_id`, `app_id_hash`, `member_id`),
  KEY `idx_wx_mini_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_wx_mini_union` (`union_id_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信小程序会员身份绑定';
