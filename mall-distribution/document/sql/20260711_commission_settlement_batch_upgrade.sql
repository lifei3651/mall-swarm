CREATE TABLE IF NOT EXISTS `dms_commission_settlement_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) NOT NULL,
  `period_start` datetime NOT NULL,
  `period_end` datetime NOT NULL,
  `cutoff_time` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0草稿锁定 1已执行 2已作废',
  `record_count` int NOT NULL DEFAULT 0,
  `total_amount` decimal(18,2) NOT NULL DEFAULT 0,
  `settled_count` int NOT NULL DEFAULT 0,
  `skipped_count` int NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  `creator_id` bigint NOT NULL,
  `creator_name` varchar(64) NOT NULL,
  `executor_id` bigint DEFAULT NULL,
  `executor_name` varchar(64) DEFAULT NULL,
  `execute_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_batch_no` (`batch_no`), KEY `idx_status_create` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度佣金结算批次';

CREATE TABLE IF NOT EXISTS `dms_commission_settlement_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `commission_record_id` bigint NOT NULL,
  `agent_id` bigint NOT NULL,
  `agent_name` varchar(64) DEFAULT NULL,
  `snapshot_amount` decimal(18,2) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0已锁定 1已结算 2已跳过',
  `skip_reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_batch_record` (`batch_id`,`commission_record_id`), KEY `idx_record` (`commission_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度佣金结算批次明细和金额快照';
