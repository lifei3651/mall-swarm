-- 订单、秒杀、余额支付、转账和提现的持久幂等请求记录。
-- 成功或进程异常遗留的处理中记录不自动删除，避免超时后重复执行资金操作。
CREATE TABLE IF NOT EXISTS `dms_idempotency_record` (
  `request_key` char(64) NOT NULL COMMENT '请求方法、路径、会员会话和客户端请求号的SHA-256',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0处理中 1成功',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_key`),
  KEY `idx_idempotency_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键业务持久幂等记录';
