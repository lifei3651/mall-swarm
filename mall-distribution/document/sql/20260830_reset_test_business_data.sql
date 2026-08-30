-- 灵启商城测试业务数据清零（适配线上 1.0.100 / 本地 1.0.101）
--
-- 保留：平台与商家管理员、商户主体、租户、商品、SKU、分类、轮播、公告、
--       装修与品牌配置、运费与服务地址、消息通道/模板/预算、奖金与风控规则、
--       直播间及直播运营配置、数据库迁移记录。
-- 清除：客户会员及会话、地址、实名、订单、交易、发货、售后、评价、客服工单、
--       会员消息及投递任务、奖金、业绩、余额、提现、团队关系、导入、移线、
--       秒杀测试活动、直播评论/预约/观看记录、商家订单结算及财务流水、幂等记录、
--       后台操作日志和后台会话。
--
-- 商品与 SKU 的测试销量会归还到库存并清零销量。两个不可登录的系统资金账户保留，
-- 金额及累计值全部归零。脚本不重置主键，不修改产品和客户配置。
--
-- 强制要求：停止应用、完成完整备份并通过隔离恢复；只允许在正式库
-- mall_distribution 或 mall_distribution_reset_verify_* 隔离验收库执行。

DROP PROCEDURE IF EXISTS `reset_test_business_data_20260830`;
DELIMITER $$

CREATE PROCEDURE `reset_test_business_data_20260830`()
BEGIN
    DECLARE v_database_name VARCHAR(128);
    DECLARE v_active_admins_before INT DEFAULT 0;
    DECLARE v_admins_before INT DEFAULT 0;
    DECLARE v_merchants_before INT DEFAULT 0;
    DECLARE v_tenants_before INT DEFAULT 0;
    DECLARE v_products_before INT DEFAULT 0;
    DECLARE v_skus_before INT DEFAULT 0;
    DECLARE v_categories_before INT DEFAULT 0;
    DECLARE v_migrations_before INT DEFAULT 0;
    DECLARE v_product_units_before DECIMAL(30, 4) DEFAULT 0;
    DECLARE v_sku_units_before DECIMAL(30, 4) DEFAULT 0;
    DECLARE v_system_members INT DEFAULT 0;
    DECLARE v_system_agents INT DEFAULT 0;
    DECLARE v_system_agent_accounts INT DEFAULT 0;
    DECLARE v_system_asset_accounts INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT DATABASE() INTO v_database_name;
    IF v_database_name <> 'mall_distribution'
       AND v_database_name NOT LIKE 'mall_distribution_reset_verify\\_%' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '拒绝执行：数据库不是正式库或指定隔离验收库';
    END IF;

    SELECT COUNT(*) INTO v_active_admins_before FROM dms_admin_user WHERE status = 1;
    SELECT COUNT(*) INTO v_admins_before FROM dms_admin_user;
    SELECT COUNT(*) INTO v_merchants_before FROM dms_merchant;
    SELECT COUNT(*) INTO v_tenants_before FROM dms_tenant;
    SELECT COUNT(*) INTO v_products_before FROM dms_shop_product;
    SELECT COUNT(*) INTO v_skus_before FROM dms_shop_sku;
    SELECT COUNT(*) INTO v_categories_before FROM dms_shop_category;
    SELECT COUNT(*) INTO v_migrations_before FROM dms_schema_migration_history;
    SELECT COALESCE(SUM(stock + sales_count), 0) INTO v_product_units_before FROM dms_shop_product;
    SELECT COALESCE(SUM(stock + sales_count), 0) INTO v_sku_units_before FROM dms_shop_sku;

    SELECT COUNT(*) INTO v_system_members
      FROM dms_shop_member
     WHERE system_account = 1
       AND user_id IN (-900000000000000001, -900000000000000005);
    SELECT COUNT(*) INTO v_system_agents
      FROM dms_agent
     WHERE user_id IN (-900000000000000001, -900000000000000005);
    SELECT COUNT(*) INTO v_system_agent_accounts
      FROM dms_agent_account
     WHERE user_id IN (-900000000000000001, -900000000000000005);
    SELECT COUNT(*) INTO v_system_asset_accounts
      FROM dms_member_asset_account
     WHERE user_id IN (-900000000000000001, -900000000000000005);

    IF v_active_admins_before < 1 OR v_tenants_before < 1
       OR v_products_before < 1 OR v_skus_before < 1 OR v_categories_before < 1
       OR v_migrations_before < 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '拒绝执行：管理员、租户、商品、分类或迁移基座不完整';
    END IF;
    IF v_system_members <> 2 OR v_system_agents <> 2
       OR v_system_agent_accounts <> 2 OR v_system_asset_accounts <> 2
       OR (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 1) <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '拒绝执行：两个系统资金账户不完整或出现重复';
    END IF;

    START TRANSACTION;

    -- 清除全部登录和后台测试操作轨迹，清理完成后必须重新登录。
    DELETE FROM dms_shop_member_session;
    DELETE FROM dms_admin_session;
    DELETE FROM dms_operation_log;
    DELETE FROM dms_idempotency_record;

    -- 会员消息、外部投递任务和接收授权；保留通道、模板与预算配置。
    DELETE FROM dms_message_delivery_receipt;
    DELETE FROM dms_message_delivery_attempt;
    DELETE FROM dms_message_delivery_task;
    DELETE FROM dms_message_recipient_authorization;
    DELETE FROM dms_member_message;

    -- 客服、实名、直播互动和商品评价。
    DELETE FROM dms_shop_service_ticket_reply;
    DELETE FROM dms_shop_service_ticket;
    DELETE FROM dms_member_real_name_attempt;
    DELETE FROM dms_member_real_name;
    DELETE FROM dms_live_comment;
    DELETE FROM dms_live_reservation;
    DELETE FROM dms_live_view_session;
    DELETE FROM dms_merchant_product_review;
    DELETE FROM dms_shop_product_review;

    -- 售后、退款、发货、订单和交易。
    DELETE FROM dms_shop_after_sale_item;
    DELETE FROM dms_shop_after_sale;
    DELETE FROM dms_finance_refund;
    DELETE FROM dms_shop_order_shipment;

    -- 奖金计算、结算、追回、关系及订单财务快照。
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

    -- 商家订单结算和财务流水；保留商户主体及账户壳。
    DELETE FROM dms_merchant_withdrawal_event;
    DELETE FROM dms_merchant_withdrawal;
    DELETE FROM dms_merchant_deposit_flow;
    DELETE FROM dms_merchant_ledger;
    DELETE FROM dms_merchant_settlement;
    UPDATE dms_merchant_account
       SET pending_amount = 0,
           available_amount = 0,
           frozen_amount = 0,
           deposit_frozen_amount = 0,
           debt_amount = 0,
           total_paid_amount = 0,
           update_time = CURRENT_TIMESTAMP;

    -- 秒杀测试数据和 ERP 业务任务；直播间、主播、预告及关联商品配置继续保留。
    DELETE FROM dms_flash_sale_reservation;
    DELETE FROM dms_flash_sale_activity;
    DELETE FROM dms_erp_sync_task;

    -- 把当前净销量归还到库存，再删除订单明细和订单。
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
    DELETE FROM dms_shop_trade;

    -- 余额、提现、历史资产档案、团队、业绩、导入和移线。
    DELETE FROM dms_withdraw_record;
    DELETE FROM dms_member_asset_flow;
    DELETE FROM dms_retired_asset_flow_archive;
    DELETE FROM dms_retired_asset_adjustment_archive;
    DELETE FROM dms_retired_asset_account_archive;
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

    -- 删除客户会员及关联账户，只保留两个内部系统资金账户。
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

    -- 系统资金账户恢复为不可登录、无关系、全零状态。
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

    -- 清理必须保持系统配置、商品数量、主数据及库存总量不变。
    IF (SELECT COUNT(*) FROM dms_admin_user) <> v_admins_before
       OR (SELECT COUNT(*) FROM dms_admin_user WHERE status = 1) <> v_active_admins_before
       OR (SELECT COUNT(*) FROM dms_merchant) <> v_merchants_before
       OR (SELECT COUNT(*) FROM dms_tenant) <> v_tenants_before
       OR (SELECT COUNT(*) FROM dms_shop_product) <> v_products_before
       OR (SELECT COUNT(*) FROM dms_shop_sku) <> v_skus_before
       OR (SELECT COUNT(*) FROM dms_shop_category) <> v_categories_before
       OR (SELECT COUNT(*) FROM dms_schema_migration_history) <> v_migrations_before
       OR (SELECT COALESCE(SUM(stock), 0) FROM dms_shop_product) <> v_product_units_before
       OR (SELECT COALESCE(SUM(stock), 0) FROM dms_shop_sku) <> v_sku_units_before
       OR (SELECT COALESCE(SUM(sales_count), 0) FROM dms_shop_product) <> 0
       OR (SELECT COALESCE(SUM(sales_count), 0) FROM dms_shop_sku) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '清理失败：配置、商品、迁移或库存不变量发生变化';
    END IF;

    IF (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 0) <> 0
       OR (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 1) <> 2
       OR (SELECT COUNT(*) FROM dms_agent) <> 2
       OR (SELECT COUNT(*) FROM dms_agent_account) <> 2
       OR (SELECT COUNT(*) FROM dms_member_asset_account) <> 2
       OR (SELECT COUNT(*) FROM dms_shop_order) <> 0
       OR (SELECT COUNT(*) FROM dms_shop_order_item) <> 0
       OR (SELECT COUNT(*) FROM dms_shop_trade) <> 0
       OR (SELECT COUNT(*) FROM dms_shop_after_sale) <> 0
       OR (SELECT COUNT(*) FROM dms_commission_record) <> 0
       OR (SELECT COUNT(*) FROM dms_member_asset_flow) <> 0
       OR (SELECT COUNT(*) FROM dms_withdraw_record) <> 0
       OR (SELECT COUNT(*) FROM dms_member_message) <> 0
       OR (SELECT COUNT(*) FROM dms_message_delivery_task) <> 0
       OR (SELECT COUNT(*) FROM dms_member_real_name) <> 0
       OR (SELECT COUNT(*) FROM dms_shop_service_ticket) <> 0
       OR (SELECT COUNT(*) FROM dms_live_comment) <> 0
       OR (SELECT COUNT(*) FROM dms_live_reservation) <> 0
       OR (SELECT COUNT(*) FROM dms_live_view_session) <> 0
       OR (SELECT COUNT(*) FROM dms_merchant_ledger) <> 0
       OR (SELECT COUNT(*) FROM dms_merchant_settlement) <> 0
       OR (SELECT COUNT(*) FROM dms_admin_session) <> 0
       OR (SELECT COUNT(*) FROM dms_shop_member_session) <> 0
       OR (SELECT COUNT(*) FROM dms_operation_log) <> 0
       OR (SELECT COUNT(*) FROM dms_idempotency_record) <> 0
       OR (SELECT COALESCE(SUM(balance + frozen_balance + total_in + total_out), 0)
             FROM dms_member_asset_account) <> 0
       OR (SELECT COALESCE(SUM(pending_amount + available_amount + frozen_amount
                             + deposit_frozen_amount + debt_amount + total_paid_amount), 0)
             FROM dms_merchant_account) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '清理失败：会员、订单或资金业务数据仍有残留';
    END IF;

    COMMIT;
END$$

DELIMITER ;

CALL `reset_test_business_data_20260830`();
DROP PROCEDURE `reset_test_business_data_20260830`;

SELECT
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 0) AS customer_members,
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 1) AS system_members,
    (SELECT COUNT(*) FROM dms_shop_order) AS orders,
    (SELECT COUNT(*) FROM dms_shop_after_sale) AS after_sales,
    (SELECT COUNT(*) FROM dms_commission_record) AS commissions,
    (SELECT COUNT(*) FROM dms_member_asset_flow) AS asset_flows,
    (SELECT COUNT(*) FROM dms_member_message) AS member_messages,
    (SELECT COUNT(*) FROM dms_shop_service_ticket) AS service_tickets,
    (SELECT COUNT(*) FROM dms_admin_user) AS admins_kept,
    (SELECT COUNT(*) FROM dms_shop_product) AS products_kept,
    (SELECT COUNT(*) FROM dms_shop_category) AS categories_kept;

SELECT
    COALESCE(SUM(balance), 0) AS total_balance,
    COALESCE(SUM(frozen_balance), 0) AS total_frozen,
    COALESCE(SUM(total_in), 0) AS total_in,
    COALESCE(SUM(total_out), 0) AS total_out
FROM dms_member_asset_account;
