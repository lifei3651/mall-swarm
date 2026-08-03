-- 订单商品款真实余额归集：
-- 1. 产品成本进入系统产品成本账户；
-- 2. 商品实付（不含运费）扣除产品成本和全部推广奖金后的剩余款进入系统剩余金额账户；
-- 3. 与奖金相同，确认收货满7天且无待处理售后才进入可支付、可转账、可提现余额；
-- 4. 退款按商品实退款、退货SKU冻结成本和奖金冲减后的净额冲回。

CREATE TABLE IF NOT EXISTS `dms_order_balance_allocation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `allocation_type` varchar(32) NOT NULL COMMENT 'PRODUCT_COST-产品成本 REMAINDER-剩余商品款',
  `target_member_id` bigint NOT NULL COMMENT '目标内部资金账户主键',
  `target_user_id` bigint NOT NULL COMMENT '目标用户ID',
  `target_agent_id` bigint NOT NULL COMMENT '目标内部资金账户资产主体ID',
  `original_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '订单初始应归集金额',
  `current_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '退款后当前应归集净额',
  `settled_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '累计已进入余额金额',
  `reversed_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '退款累计冲回金额',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待结算 1-已结算 2-已全部冲回/无需结算',
  `settle_time` datetime DEFAULT NULL COMMENT '首次结算时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_type` (`order_id`, `allocation_type`),
  KEY `idx_status_settle` (`status`, `settle_time`),
  KEY `idx_target_agent` (`target_agent_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品款真实余额归集表';
