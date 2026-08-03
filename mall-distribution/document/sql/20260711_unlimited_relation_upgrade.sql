-- 支持同一会员多次移线并保留每次历史关系。
-- 有效关系唯一性由移线事务保证，历史失效关系允许保留多条。
ALTER TABLE `dms_agent_relation` DROP INDEX `uk_user_parent`;
ALTER TABLE `dms_agent_relation`
  ADD INDEX `idx_user_parent_valid` (`user_id`, `parent_user_id`, `is_valid`);
