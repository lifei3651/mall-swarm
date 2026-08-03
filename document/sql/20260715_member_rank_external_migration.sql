-- 外部团队平移期初数据（商品不承载会员卡级或晋级条件）

CREATE TABLE IF NOT EXISTS `dms_migration_baseline` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) NOT NULL COMMENT '平移批次编号',
  `agent_id` bigint NOT NULL COMMENT '迁入后代理ID',
  `user_id` bigint NOT NULL COMMENT '迁入后会员编号',
  `external_member_code` varchar(128) NOT NULL COMMENT '原平台会员编号',
  `historical_order_count` int NOT NULL DEFAULT 0 COMMENT '平移前历史有效订单数',
  `historical_personal_performance` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '平移前个人业绩',
  `historical_team_performance` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '平移前团队业绩',
  `initial_level` tinyint NOT NULL DEFAULT 1 COMMENT '迁入时卡级',
  `cutover_time` datetime NOT NULL COMMENT '业绩切换时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_migration_agent` (`agent_id`),
  UNIQUE KEY `uk_migration_external_code` (`external_member_code`),
  KEY `idx_migration_batch` (`batch_no`),
  KEY `idx_migration_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部团队平移期初基线';
