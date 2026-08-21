-- 业绩排行榜改为数据库聚合分页后的查询索引（MySQL 8）
SET @schema_name := DATABASE();

SET @sql := IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name
             AND table_name = 'dms_order_performance_detail'
             AND index_name = 'idx_perf_ranking_status_time_target'),
    'SELECT 1',
    'CREATE INDEX idx_perf_ranking_status_time_target ON dms_order_performance_detail(status, order_time, target_agent_id, relation_level)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name
             AND table_name = 'dms_agent_relation'
             AND index_name = 'idx_relation_parent_valid_agent'),
    'SELECT 1',
    'CREATE INDEX idx_relation_parent_valid_agent ON dms_agent_relation(parent_agent_id, is_valid, agent_id)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
