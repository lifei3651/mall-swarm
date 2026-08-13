-- 商城基座业务数据清理脚本（2026-08-13）
--
-- 目标：清除测试会员、团队、订单、售后、奖金、余额和业绩等业务数据，
--       保留商城配置、管理员、商品、SKU、分类、轮播、公告、地址模板、
--       物流/ERP配置、奖金/风控配置、租户配置版本和数据库迁移记录。
--
-- 强制执行边界：
--   1. 仅允许在 mall_distribution 数据库执行；
--   2. 执行前必须停掉 mall-distribution 服务并完成全量备份；
--   3. 必须存在启用的管理员、租户、商品、分类，以及两个预置系统资金账户；
--   4. 任一语句失败时整笔业务数据清理回滚；
--   5. 不重置主键，不删除商品、配置和管理员，不删除迁移记录。

USE `mall_distribution`;

DROP PROCEDURE IF EXISTS `reset_commerce_foundation_20260813`;
DELIMITER $$

CREATE PROCEDURE `reset_commerce_foundation_20260813`()
BEGIN
    DECLARE v_database_name VARCHAR(128);
    DECLARE v_active_admins INT DEFAULT 0;
    DECLARE v_tenants INT DEFAULT 0;
    DECLARE v_products INT DEFAULT 0;
    DECLARE v_categories INT DEFAULT 0;
    DECLARE v_system_members INT DEFAULT 0;
    DECLARE v_system_agents INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT DATABASE() INTO v_database_name;
    SELECT COUNT(*) INTO v_active_admins FROM dms_admin_user WHERE status = 1;
    SELECT COUNT(*) INTO v_tenants FROM dms_tenant;
    SELECT COUNT(*) INTO v_products FROM dms_shop_product;
    SELECT COUNT(*) INTO v_categories FROM dms_shop_category;
    SELECT COUNT(*) INTO v_system_members
      FROM dms_shop_member
     WHERE system_account = 1
       AND user_id IN (-900000000000000001, -900000000000000005);
    SELECT COUNT(*) INTO v_system_agents
      FROM dms_agent
     WHERE user_id IN (-900000000000000001, -900000000000000005);

    IF v_database_name <> 'mall_distribution' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '拒绝执行：当前数据库不是 mall_distribution';
    END IF;
    IF v_active_admins < 1 OR v_tenants < 1 OR v_products < 1 OR v_categories < 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '拒绝执行：管理员、租户、商品或分类基座不完整';
    END IF;
    IF v_system_members <> 2 OR v_system_agents <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '拒绝执行：两个系统资金账户不完整或出现重复';
    END IF;

    START TRANSACTION;

    -- 清除所有浏览器会话，清库完成后后台需重新登录。
    DELETE FROM dms_admin_session;
    DELETE FROM dms_shop_member_session;

    -- 售后、退款、发货、评价。
    DELETE FROM dms_shop_after_sale_item;
    DELETE FROM dms_shop_after_sale;
    DELETE FROM dms_finance_refund;
    DELETE FROM dms_shop_order_shipment;
    DELETE FROM dms_shop_product_review;

    -- 奖金计算、结算、追回和订单财务快照。
    DELETE FROM dms_commission_clawback;
    DELETE FROM dms_commission_settlement_item;
    DELETE FROM dms_commission_settlement_batch;
    DELETE FROM dms_commission_record;
    DELETE FROM dms_bonus_calculation_snapshot;
    DELETE FROM dms_bonus_calculation_task;
    DELETE FROM dms_order_balance_allocation;
    DELETE FROM dms_order_company_share;
    DELETE FROM dms_order_finance;
    DELETE FROM dms_order_performance_detail;
    DELETE FROM dms_order_pv_detail;
    DELETE FROM dms_order_relation_snapshot;

    -- 秒杀活动和占位、ERP业务任务。
    DELETE FROM dms_flash_sale_reservation;
    DELETE FROM dms_flash_sale_activity;
    DELETE FROM dms_erp_sync_task;

    -- 订单创建时已经扣减库存并增加销量；将仍占用的销量完整还原为库存。
    UPDATE dms_shop_sku
       SET stock = stock + sales_count,
           sales_count = 0,
           update_time = CURRENT_TIMESTAMP
     WHERE sales_count <> 0;
    UPDATE dms_shop_product
       SET stock = stock + sales_count,
           sales_count = 0,
           update_time = CURRENT_TIMESTAMP
     WHERE sales_count <> 0;

    DELETE FROM dms_shop_order_item;
    DELETE FROM dms_shop_order;

    -- 余额、提现及历史资产档案。
    DELETE FROM dms_withdraw_record;
    DELETE FROM dms_member_asset_flow;
    DELETE FROM dms_retired_asset_flow_archive;
    DELETE FROM dms_retired_asset_adjustment_archive;
    DELETE FROM dms_retired_asset_account_archive;

    -- 团队、业绩、导入和移线业务记录。
    DELETE FROM dms_subordinate_contribution;
    DELETE FROM dms_performance_ranking;
    DELETE FROM dms_performance_view_permission;
    DELETE FROM dms_agent_performance_summary;
    DELETE FROM dms_agent_change_log;
    DELETE FROM dms_line_change_application;
    DELETE FROM dms_agent_relation;
    DELETE FROM dms_migration_baseline;
    DELETE FROM dms_import_detail;
    DELETE FROM dms_import_batch;

    -- 删除客户会员及其资金/代理账户，只保留两个内部系统资金账户。
    DELETE FROM dms_member_asset_account
     WHERE user_id NOT IN (-900000000000000001, -900000000000000005);
    DELETE FROM dms_agent_account
     WHERE user_id NOT IN (-900000000000000001, -900000000000000005);
    DELETE FROM dms_agent
     WHERE user_id NOT IN (-900000000000000001, -900000000000000005);
    DELETE FROM dms_shop_address;
    DELETE FROM dms_shop_member
     WHERE system_account = 0
        OR user_id NOT IN (-900000000000000001, -900000000000000005);

    -- 系统资金账户回到全零、禁用且不参与会员团队的基座状态。
    UPDATE dms_shop_member
       SET inviter_id = NULL,
           status = 0,
           failed_login_count = 0,
           lock_time = NULL,
           pay_password_failed_count = 0,
           pay_password_lock_time = NULL,
           last_login_time = NULL,
           update_time = CURRENT_TIMESTAMP
     WHERE system_account = 1
       AND user_id IN (-900000000000000001, -900000000000000005);

    UPDATE dms_agent
       SET agent_level = 1,
           parent_id = NULL,
           ancestor_ids = NULL,
           level_depth = 1,
           status = 2,
           update_time = CURRENT_TIMESTAMP
     WHERE user_id IN (-900000000000000001, -900000000000000005);

    UPDATE dms_agent_account
       SET total_commission = 0,
           settled_commission = 0,
           unsettled_commission = 0,
           frozen_commission = 0,
           withdrawn_amount = 0,
           available_balance = 0,
           total_orders = 0,
           total_team_members = 0,
           update_time = CURRENT_TIMESTAMP
     WHERE user_id IN (-900000000000000001, -900000000000000005);

    UPDATE dms_member_asset_account
       SET balance = 0,
           frozen_balance = 0,
           total_in = 0,
           total_out = 0,
           update_time = CURRENT_TIMESTAMP
     WHERE user_id IN (-900000000000000001, -900000000000000005);

    -- 特殊业务模式保留代码和配置字段，但基座默认关闭，不允许误产生特殊订单或奖金。
    UPDATE dms_tenant
       SET flash_sale_enabled = 0,
           flash_sale_bonus_mode = 'NONE',
           repurchase_mall_enabled = 0,
           repurchase_bonus_mode = 'NONE',
           update_time = CURRENT_TIMESTAMP;

    -- 清除测试操作轨迹；管理员本身及配置版本历史继续保留。
    DELETE FROM dms_operation_log;

    COMMIT;
END$$

DELIMITER ;

CALL `reset_commerce_foundation_20260813`();
DROP PROCEDURE `reset_commerce_foundation_20260813`;

-- 执行后验收摘要：所有客户业务数必须为 0，内部系统账户必须恰好为 2 且金额为 0。
SELECT
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 0) AS customer_members,
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 1) AS system_members,
    (SELECT COUNT(*) FROM dms_agent WHERE status = 1) AS active_agents,
    (SELECT COUNT(*) FROM dms_shop_order) AS orders,
    (SELECT COUNT(*) FROM dms_shop_after_sale) AS after_sales,
    (SELECT COUNT(*) FROM dms_commission_record) AS commissions,
    (SELECT COUNT(*) FROM dms_commission_clawback) AS clawbacks,
    (SELECT COUNT(*) FROM dms_member_asset_flow) AS asset_flows,
    (SELECT COUNT(*) FROM dms_withdraw_record) AS withdrawals,
    (SELECT COUNT(*) FROM dms_flash_sale_activity) AS flash_sale_activities,
    (SELECT COUNT(*) FROM dms_erp_sync_task) AS erp_tasks,
    (SELECT COUNT(*) FROM dms_shop_member_session) AS member_sessions,
    (SELECT COUNT(*) FROM dms_admin_session) AS admin_sessions;

SELECT
    COALESCE(SUM(balance), 0) AS total_balance,
    COALESCE(SUM(frozen_balance), 0) AS total_frozen,
    COALESCE(SUM(total_in), 0) AS total_in,
    COALESCE(SUM(total_out), 0) AS total_out
FROM dms_member_asset_account;

SELECT id, product_name, stock, sales_count
FROM dms_shop_product
ORDER BY id;

SELECT id, product_id, sku_name, stock, sales_count
FROM dms_shop_sku
ORDER BY id;
