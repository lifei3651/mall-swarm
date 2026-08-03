-- 支付回调/任务重试时，防止同一订单向同一代理重复生成同层级佣金。
-- 执行前请先排查并清理历史重复数据。
ALTER TABLE `dms_commission_record`
  ADD UNIQUE KEY `uk_order_agent_level` (`order_id`, `agent_id`, `commission_level`);
