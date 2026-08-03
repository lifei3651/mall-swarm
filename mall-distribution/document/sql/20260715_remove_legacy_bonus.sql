-- 彻底移除旧三级佣金、可编辑奖金节点及旧版本数据。
-- 执行前必须先运行 20260714_new_retail_default_rule.sql，确保正式版本已存在。

SET @new_retail_version_id := (
  SELECT id
  FROM dms_commission_rule_version
  WHERE tenant_id = 1 AND version_no = 'NEW_RETAIL_SIMPLE_DEFAULT'
  ORDER BY id
  LIMIT 1
);

ALTER TABLE dms_commission_record
  ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1 AFTER id,
  ADD COLUMN rule_version_id bigint DEFAULT NULL AFTER tenant_id,
  ADD COLUMN bonus_type varchar(32) DEFAULT NULL AFTER commission_level;

UPDATE dms_commission_record
SET tenant_id = 1,
    rule_version_id = @new_retail_version_id,
    bonus_type = CASE
      WHEN remark LIKE '%直推奖%' THEN 'DIRECT_REWARD'
      WHEN remark LIKE '%董事无限层%' THEN 'DIRECTOR_SHARE'
      WHEN commission_level = 1 THEN 'DIRECT_REWARD'
      ELSE 'LEGACY_HISTORY'
    END;

ALTER TABLE dms_commission_record
  MODIFY COLUMN bonus_type varchar(32) NOT NULL,
  MODIFY COLUMN commission_level int NOT NULL COMMENT '与下单人的关系深度（不限层）',
  DROP INDEX uk_order_agent_level,
  ADD UNIQUE KEY uk_order_agent_bonus (order_id, agent_id, bonus_type),
  DROP COLUMN rule_id;

-- 消除旧版 tinyint 和路径长度对团队深度的隐性上限。
ALTER TABLE dms_agent
  MODIFY COLUMN ancestor_ids text DEFAULT NULL COMMENT '所有上级ID路径，不限层';
ALTER TABLE dms_agent_relation
  MODIFY COLUMN relation_level int NOT NULL DEFAULT 1 COMMENT '关系深度，不限层',
  MODIFY COLUMN relation_path text DEFAULT NULL COMMENT '关系路径，不限层';
ALTER TABLE dms_order_relation_snapshot
  MODIFY COLUMN relation_level int NOT NULL COMMENT '支付时关系深度，不限层',
  MODIFY COLUMN relation_path text DEFAULT NULL COMMENT '支付时关系路径，不限层';
ALTER TABLE dms_order_performance_detail
  MODIFY COLUMN relation_level int NOT NULL COMMENT '业绩关系深度，0为本人，团队不限层';
ALTER TABLE dms_subordinate_contribution
  MODIFY COLUMN relation_level int NOT NULL COMMENT '与下属的关系深度，不限层';

UPDATE dms_order_relation_snapshot SET rule_version_id = @new_retail_version_id WHERE tenant_id = 1;
UPDATE dms_bonus_calculation_task SET rule_version_id = @new_retail_version_id WHERE tenant_id = 1;
UPDATE dms_bonus_calculation_snapshot SET rule_version_id = @new_retail_version_id WHERE tenant_id = 1;
UPDATE dms_bonus_wallet_allocation SET rule_version_id = @new_retail_version_id WHERE tenant_id = 1;

DELETE FROM dms_commission_rule_version
WHERE tenant_id = 1 AND id <> @new_retail_version_id;

UPDATE dms_commission_rule_version
SET status = 1,
    version_name = '新零售简版奖金计划',
    remark = '唯一固定方案；触发升级的订单按支付前卡级计奖，后续新订单全部按新卡级计奖。'
WHERE id = @new_retail_version_id;

DROP TABLE IF EXISTS dms_bonus_rule_node;
DROP TABLE IF EXISTS dms_commission_rule;
