-- 清理灵启商城全部测试业务数据，保留可直接交付的商城基座。
--
-- 保留：管理员与权限、租户及品牌配置、奖金与风控规则、运费/ERP配置、
--       商品、SKU、分类、轮播图和公告。
-- 删除：全部旧客户会员、订单、支付、奖金、
--       售后、余额、提现、关系树、业绩、地址、评价、会话及测试日志。
--
-- 删除后重新创建两个“系统内部资金账户”：
--   SYSTEM_REMAINDER：剩余商品款归集；
--   SYSTEM_PRODUCT_COST：产品成本归集。
-- 这两条记录不是客户会员，不可登录、不进入会员数量、关系树或等级统计；
-- 它们只用于保证新订单的资金归集链路完整，初始余额和累计金额全部为0。
--
-- 执行前必须完整备份 mall_distribution，并停止业务应用，防止清理期间写入。

SET @schema_name := DATABASE();
SET @column_sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'dms_shop_member'
       AND COLUMN_NAME = 'system_account') = 0,
    'ALTER TABLE dms_shop_member ADD COLUMN system_account TINYINT NOT NULL DEFAULT 0 COMMENT ''系统内部资金账户：0-否 1-是'' AFTER status',
    'SELECT ''system_account column ready'''
);
PREPARE column_guard FROM @column_sql;
EXECUTE column_guard;
DEALLOCATE PREPARE column_guard;

SET @foundation_ready := (
    (SELECT COUNT(*) FROM dms_admin_user WHERE status = 1) >= 1
    AND (SELECT COUNT(*) FROM dms_tenant WHERE status = 1) >= 1
    AND (SELECT COUNT(*) FROM dms_commission_rule_version WHERE status = 1) >= 1
    AND (SELECT COUNT(*) FROM dms_shop_product) >= 1
    AND (SELECT COUNT(*) FROM dms_shop_category) >= 1
);
SET @guard_sql := IF(
    @foundation_ready,
    'SELECT ''cleanup prerequisites passed'' AS result',
    'SELECT * FROM __cleanup_aborted_foundation_incomplete__'
);
PREPARE cleanup_guard FROM @guard_sql;
EXECUTE cleanup_guard;
DEALLOCATE PREPARE cleanup_guard;

START TRANSACTION;

-- 订单、支付、奖金、结算、退款、发货和业绩快照。
DELETE FROM dms_shop_after_sale_item;
DELETE FROM dms_shop_after_sale;
DELETE FROM dms_shop_order_shipment;
DELETE FROM dms_shop_product_review;
DELETE FROM dms_commission_clawback;
DELETE FROM dms_finance_refund;
DELETE FROM dms_commission_settlement_item;
DELETE FROM dms_commission_settlement_batch;
DELETE FROM dms_commission_record;
DELETE FROM dms_bonus_calculation_task;
DELETE FROM dms_bonus_calculation_snapshot;
DELETE FROM dms_order_balance_allocation;
DELETE FROM dms_order_company_share;
DELETE FROM dms_order_finance;
DELETE FROM dms_order_performance_detail;
DELETE FROM dms_order_relation_snapshot;
DELETE FROM dms_order_pv_detail;
DELETE FROM dms_shop_order_item;
DELETE FROM dms_shop_order;

-- 余额、提现及历史旧资产记录。
DELETE FROM dms_member_asset_flow;
DELETE FROM dms_withdraw_record;
DELETE FROM dms_retired_asset_adjustment_archive;
DELETE FROM dms_retired_asset_flow_archive;
DELETE FROM dms_retired_asset_account_archive;

-- 团队关系、移线、导入和业绩统计。
DELETE FROM dms_line_change_application;
DELETE FROM dms_agent_relation;
DELETE FROM dms_agent_change_log;
DELETE FROM dms_agent_performance_summary;
DELETE FROM dms_subordinate_contribution;
DELETE FROM dms_performance_ranking;
DELETE FROM dms_performance_view_permission;
DELETE FROM dms_migration_baseline;
DELETE FROM dms_import_detail;
DELETE FROM dms_import_batch;
DELETE FROM dms_erp_sync_task;

-- 登录状态、地址及测试操作日志；保留管理员和权限本身。
DELETE FROM dms_shop_member_session;
DELETE FROM dms_admin_session;
DELETE FROM dms_shop_address;
DELETE FROM dms_operation_log;
UPDATE dms_admin_user
SET failed_login_count = 0,
    lock_time = NULL,
    last_login_time = NULL;

-- 删除所有旧会员和旧资金账户，不沿用旧账号的手机号、登录名、密码或关系。
DELETE FROM dms_agent_account;
DELETE FROM dms_member_asset_account;
DELETE FROM dms_agent;
DELETE FROM dms_shop_member;

-- 从零创建不可登录的内部资金归集账户；负数 user_id 为系统保留标识。
INSERT INTO dms_shop_member
    (id, user_id, phone, username, password_hash, nickname, invite_code,
     inviter_id, status, system_account)
VALUES
    (1, -900000000000000001, 'SYS-REMAINDER-0001', 'SYSTEM_REMAINDER', '$2y$12$uau8o8usTBUf58GxRFZGdubD4KaGP9tuO3IKyvm1pFHVkk0aab672',
     '系统剩余金额账户', NULL, NULL, 0, 1),
    (5, -900000000000000005, 'SYS-COST-0005', 'SYSTEM_PRODUCT_COST', '$2y$12$uau8o8usTBUf58GxRFZGdubD4KaGP9tuO3IKyvm1pFHVkk0aab672',
     '系统产品成本账户', NULL, NULL, 0, 1);

INSERT INTO dms_agent
    (id, user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids,
     level_depth, invite_code, phone, status, source_type, remark)
VALUES
    (1, -900000000000000001, 'SYS_REMAINDER', '系统剩余金额账户', 1,
     NULL, NULL, 1, 'SYSREM01', NULL, 2, 3, '内部资金归集账户，不属于客户会员'),
    (5, -900000000000000005, 'SYS_PRODUCT_COST', '系统产品成本账户', 1,
     NULL, NULL, 1, 'SYSCOST1', NULL, 2, 3, '内部资金归集账户，不属于客户会员');

INSERT INTO dms_agent_account
    (id, agent_id, user_id, total_commission, settled_commission,
     unsettled_commission, frozen_commission, withdrawn_amount,
     available_balance, total_orders, total_team_members)
VALUES
    (1, 1, -900000000000000001, 0, 0, 0, 0, 0, 0, 0, 0),
    (5, 5, -900000000000000005, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO dms_member_asset_account
    (id, agent_id, user_id, asset_code, asset_name, balance,
     frozen_balance, total_in, total_out)
VALUES
    (1, 1, -900000000000000001, 'CASH_BONUS', '余额', 0, 0, 0, 0),
    (5, 5, -900000000000000005, 'CASH_BONUS', '余额', 0, 0, 0, 0);

-- 保留商品配置，只清除测试订单带来的销量。
UPDATE dms_shop_product SET sales_count = 0;
UPDATE dms_shop_sku SET sales_count = 0;

SET @cleanup_verified := (
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 0) = 0
    AND (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 1) = 2
    AND (SELECT COUNT(*) FROM dms_shop_member WHERE id IN (1, 5) AND system_account = 1 AND status = 0) = 2
    AND (SELECT COUNT(*) FROM dms_agent WHERE id IN (1, 5) AND status = 2) = 2
    AND (SELECT COUNT(*) FROM dms_shop_order) = 0
    AND (SELECT COUNT(*) FROM dms_commission_record) = 0
    AND (SELECT COUNT(*) FROM dms_member_asset_flow) = 0
    AND (SELECT COALESCE(SUM(balance), 0) FROM dms_member_asset_account) = 0
    AND (SELECT COALESCE(SUM(total_in + total_out), 0) FROM dms_member_asset_account) = 0
    AND (SELECT COUNT(*) FROM dms_agent_relation) = 0
    AND (SELECT COUNT(*) FROM dms_admin_user WHERE status = 1) >= 1
    AND (SELECT COUNT(*) FROM dms_shop_product) >= 1
);
SET @verify_sql := IF(
    @cleanup_verified,
    'SELECT ''cleanup verification passed'' AS result',
    'SELECT * FROM __cleanup_aborted_verification_failed__'
);
PREPARE cleanup_verify FROM @verify_sql;
EXECUTE cleanup_verify;
DEALLOCATE PREPARE cleanup_verify;

COMMIT;

SELECT 'customer_members' AS item, COUNT(*) AS value
FROM dms_shop_member WHERE system_account = 0
UNION ALL SELECT 'internal_fund_accounts', COUNT(*) FROM dms_shop_member WHERE system_account = 1
UNION ALL SELECT 'orders', COUNT(*) FROM dms_shop_order
UNION ALL SELECT 'commission_records', COUNT(*) FROM dms_commission_record
UNION ALL SELECT 'asset_flows', COUNT(*) FROM dms_member_asset_flow
UNION ALL SELECT 'member_total_balance', CAST(COALESCE(SUM(balance), 0) AS CHAR) FROM dms_member_asset_account
UNION ALL SELECT 'products_kept', COUNT(*) FROM dms_shop_product
UNION ALL SELECT 'categories_kept', COUNT(*) FROM dms_shop_category
UNION ALL SELECT 'active_admins_kept', COUNT(*) FROM dms_admin_user WHERE status = 1;
