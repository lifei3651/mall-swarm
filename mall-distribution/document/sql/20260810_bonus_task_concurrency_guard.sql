-- 奖金任务并发幂等保护。
-- 执行前先运行第一条查询；如有结果，说明历史上已有重复任务，应先人工核对，脚本不会删除任何数据。
SELECT order_id, COUNT(*) AS duplicate_count
FROM dms_bonus_calculation_task
GROUP BY order_id
HAVING COUNT(*) > 1;

-- 无重复记录时执行；数据库唯一键保证同一订单最多只有一个奖金计算任务。
-- 已存在唯一键时安全跳过，便于部署脚本重复执行。
SET @bonus_task_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'dms_bonus_calculation_task'
    AND index_name = 'uk_bonus_task_order'
);
SET @bonus_task_index_sql = IF(
  @bonus_task_index_exists = 0,
  'ALTER TABLE dms_bonus_calculation_task ADD UNIQUE KEY uk_bonus_task_order (order_id)',
  'SELECT 1'
);
PREPARE bonus_task_index_stmt FROM @bonus_task_index_sql;
EXECUTE bonus_task_index_stmt;
DEALLOCATE PREPARE bonus_task_index_stmt;
