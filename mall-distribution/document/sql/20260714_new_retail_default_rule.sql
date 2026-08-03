-- 新零售简版为当前唯一启用的奖金版本。
-- 运行时由 CommissionServiceImpl 按版本号执行固定的八级晋升、直推奖和董事分红规则；
-- 此脚本只负责保证每个商城只启用代码固化的新零售正式方案。

UPDATE dms_commission_rule_version
SET status = 0
WHERE tenant_id = 1;

INSERT INTO dms_commission_rule_version
  (tenant_id, version_no, version_name, status, effective_time, remark)
SELECT 1, 'NEW_RETAIL_SIMPLE_DEFAULT', '新零售简版奖金计划', 1, NOW(),
       '唯一固定方案；触发升级的订单按支付前卡级计奖，后续新订单全部按新卡级计奖。'
WHERE NOT EXISTS (
  SELECT 1 FROM dms_commission_rule_version
  WHERE tenant_id = 1 AND version_no = 'NEW_RETAIL_SIMPLE_DEFAULT'
);

UPDATE dms_commission_rule_version
SET status = CASE WHEN version_no = 'NEW_RETAIL_SIMPLE_DEFAULT' THEN 1 ELSE 0 END,
    version_name = CASE WHEN version_no = 'NEW_RETAIL_SIMPLE_DEFAULT' THEN '新零售简版奖金计划' ELSE version_name END,
    remark = CASE WHEN version_no = 'NEW_RETAIL_SIMPLE_DEFAULT'
                  THEN '唯一固定方案；触发升级的订单按支付前卡级计奖，后续新订单全部按新卡级计奖。'
                  ELSE remark END
WHERE tenant_id = 1;
