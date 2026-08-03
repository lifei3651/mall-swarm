-- H2数据库测试数据

DELETE FROM dms_erp_sync_task;
DELETE FROM dms_erp_integration;
DELETE FROM dms_performance_ranking;
DELETE FROM dms_bonus_calculation_task;
DELETE FROM dms_bonus_calculation_snapshot;
DELETE FROM dms_order_pv_detail;
DELETE FROM dms_product_pv_config;
DELETE FROM dms_operation_log;
DELETE FROM dms_admin_session;
DELETE FROM dms_admin_user;
DELETE FROM dms_member_asset_flow;
DELETE FROM dms_member_asset_account;
DELETE FROM dms_order_balance_allocation;
DELETE FROM dms_tenant_display_config;
DELETE FROM dms_finance_risk_rule;
DELETE FROM dms_commission_clawback;
DELETE FROM dms_finance_refund;
DELETE FROM dms_order_company_share;
DELETE FROM dms_order_finance;
DELETE FROM dms_shop_after_sale;
DELETE FROM dms_shop_product_review;
DELETE FROM dms_shop_order_shipment;
DELETE FROM dms_shop_order_item;
DELETE FROM dms_shop_order;
DELETE FROM dms_shop_sku;
DELETE FROM dms_shop_product;
DELETE FROM dms_shop_notice;
DELETE FROM dms_shop_banner;
DELETE FROM dms_shop_category;
DELETE FROM dms_shop_address;
DELETE FROM dms_shop_member_session;
DELETE FROM dms_shop_member;
DELETE FROM dms_performance_view_permission;
DELETE FROM dms_distribution_setting;
DELETE FROM dms_commission_rule_version;
DELETE FROM dms_tenant;
DELETE FROM dms_import_detail;
DELETE FROM dms_import_batch;
DELETE FROM dms_subordinate_contribution;
DELETE FROM dms_agent_performance_summary;
DELETE FROM dms_order_performance_detail;
DELETE FROM dms_agent_change_log;
DELETE FROM dms_withdraw_record;
DELETE FROM dms_agent_account;
DELETE FROM dms_commission_record;
DELETE FROM dms_agent_relation;
DELETE FROM dms_agent;

INSERT INTO dms_tenant_display_config
(tenant_id, show_pv, show_team_performance, show_bonus_source, show_bonus_flow, show_profit, show_rank,
 show_binary_area, show_retail_module, show_store_module, show_company_share, extra_config_json)
VALUES
(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL);

INSERT INTO dms_admin_user
  (id, username, password_hash, salt, nickname, role_code, permissions, status)
VALUES
  (1, 'admin', '9caec3496b444e62944109574e4a98a3a1cde7f063c9e1c6c5700576f3ab773f', 'admin-default-salt', '超级管理员', 'SUPER_ADMIN', '*', 1);

-- ============================================================
-- 1. 代理数据
-- ============================================================

-- A: 一星董事，用于验证无限层团队分红
INSERT INTO dms_agent (id, user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids, level_depth, invite_code, status, source_type)
VALUES (1, 1001, 'AG001', '张三(A)', 5, NULL, NULL, 1, 'INV001', 1, 1);

-- B: VIP会员，上级是A
INSERT INTO dms_agent (id, user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids, level_depth, invite_code, status, source_type)
VALUES (2, 1002, 'AG002', '李四(B)', 2, 1, '1', 2, 'INV002', 1, 2);

-- C: 会员，上级是B
INSERT INTO dms_agent (id, user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids, level_depth, invite_code, status, source_type)
VALUES (3, 1003, 'AG003', '王五(C)', 1, 2, '1,2', 3, 'INV003', 1, 2);

-- D: 代理（用于切线测试）
INSERT INTO dms_agent (id, user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids, level_depth, invite_code, status, source_type)
VALUES (4, 1004, 'AG004', '赵六(D)', 4, NULL, NULL, 1, 'INV004', 1, 1);

-- E: 顶级代理（用于切线测试）
INSERT INTO dms_agent (id, user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids, level_depth, invite_code, status, source_type)
VALUES (5, 1005, 'AG005', '钱七(E)', 1, NULL, NULL, 1, 'INV005', 1, 1);

INSERT INTO dms_tenant (id, tenant_code, tenant_name, brand_name, theme_color, product_template, status)
VALUES (1, 'DEFAULT', '商城运营主体', '商城', '#0f766e', 'standard', 1);

INSERT INTO dms_commission_rule_version
  (id, tenant_id, version_no, version_name, status, effective_time, remark)
VALUES
  (1, 1, 'NEW_RETAIL_SIMPLE_DEFAULT', '新零售正式奖金方案', 1, CURRENT_TIMESTAMP,
   '唯一固定方案：八级晋升、直推奖、董事无限层团队分红');

INSERT INTO dms_shop_category (id, tenant_id, category_name, icon_url, sort_order, status, remark)
VALUES
(1, 1, '护理套装', NULL, 100, 1, '首页推荐分类'),
(2, 1, '健康生活', NULL, 90, 1, '复购商品分类');

INSERT INTO dms_shop_banner (id, tenant_id, title, image_url, link_type, link_value, sort_order, status, remark)
VALUES
(1, 1, '商城精选套装', 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=1400&q=80', 'PRODUCT', '1', 100, 1, '首页主轮播'),
(2, 1, '家庭复购活动', 'https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=1400&q=80', 'CATEGORY', '健康生活', 90, 1, '分类活动轮播');

INSERT INTO dms_shop_notice (id, tenant_id, title, content, sort_order, status)
VALUES
(1, 1, '内部测试商城已开启', '当前为内部全流程测试环境，正式支付通道完成商户配置后启用；生产环境不开放模拟支付。', 100, 1);

INSERT INTO dms_shop_product (id, tenant_id, product_no, product_name, subtitle, category_name, cover_url, sale_price, market_price, cost_amount, pv_value, bv_value, stock, sales_count, sort_order, status, detail)
VALUES
(1, 1, 'LQ-SPU-001', '轻奢焕活礼盒', '适合新客体验的高转化入门套装', '护理套装', 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=900&q=80', 299.00, 399.00, 118.00, 220.00, 220.00, 500, 32, 100, 1, '包含基础护理组合，适合日常复购和新客体验。'),
(2, 1, 'LQ-SPU-002', '每日能量组合', '家庭囤货装，适合复购与团队活动', '健康生活', 'https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=900&q=80', 198.00, 268.00, 72.00, 150.00, 150.00, 800, 61, 90, 1, '围绕日常健康生活场景设计，适合活动套餐。');

INSERT INTO dms_shop_sku (id, product_id, sku_no, sku_name, attrs_json, sale_price, market_price, cost_amount, pv_value, bv_value, stock, sales_count, status)
VALUES
(1, 1, 'LQ-SKU-001-A', '标准装', '{"规格":"标准装"}', 299.00, 399.00, 118.00, 220.00, 220.00, 300, 0, 1),
(2, 1, 'LQ-SKU-001-B', '双盒装', '{"规格":"双盒装"}', 568.00, 798.00, 230.00, 420.00, 420.00, 200, 0, 1),
(3, 2, 'LQ-SKU-002-A', '家庭装', '{"规格":"家庭装"}', 198.00, 268.00, 72.00, 150.00, 150.00, 800, 0, 1);

-- ============================================================
-- 2. 代理关系数据
-- ============================================================

-- A的直属关系（自己）
INSERT INTO dms_agent_relation (user_id, agent_id, parent_user_id, parent_agent_id, relation_level, relation_path, is_valid, bind_type)
VALUES (1001, 1, NULL, NULL, 0, '1', 1, 1);

-- B→A的关系（一级）
INSERT INTO dms_agent_relation (user_id, agent_id, parent_user_id, parent_agent_id, relation_level, relation_path, is_valid, bind_type)
VALUES (1002, 2, 1001, 1, 1, '1/2', 1, 1);

-- C→B的关系（一级）
INSERT INTO dms_agent_relation (user_id, agent_id, parent_user_id, parent_agent_id, relation_level, relation_path, is_valid, bind_type)
VALUES (1003, 3, 1002, 2, 1, '1/2/3', 1, 1);

-- C→A的关系（二级）
INSERT INTO dms_agent_relation (user_id, agent_id, parent_user_id, parent_agent_id, relation_level, relation_path, is_valid, bind_type)
VALUES (1003, 3, 1001, 1, 2, '1/3', 1, 1);

-- D的直属关系（自己）
INSERT INTO dms_agent_relation (user_id, agent_id, parent_user_id, parent_agent_id, relation_level, relation_path, is_valid, bind_type)
VALUES (1004, 4, NULL, NULL, 0, '4', 1, 1);

-- E的直属关系（自己）
INSERT INTO dms_agent_relation (user_id, agent_id, parent_user_id, parent_agent_id, relation_level, relation_path, is_valid, bind_type)
VALUES (1005, 5, NULL, NULL, 0, '5', 1, 1);

-- ============================================================
-- 3. 代理账户数据
-- ============================================================

INSERT INTO dms_agent_account (agent_id, user_id, total_commission, settled_commission, unsettled_commission, frozen_commission, withdrawn_amount, available_balance, total_orders, total_team_members)
VALUES
(1, 1001, 0, 0, 0, 0, 0, 0, 0, 2),
(2, 1002, 0, 0, 0, 0, 0, 0, 0, 1),
(3, 1003, 0, 0, 0, 0, 0, 0, 0, 0),
(4, 1004, 0, 0, 0, 0, 0, 0, 0, 0),
(5, 1005, 0, 0, 0, 0, 0, 0, 0, 0);

-- ============================================================
-- 5. 模拟订单业绩数据
-- ============================================================

-- 场景1：C下单10000元
-- C的个人业绩
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10001, 'ORD20240630001', 10000.00, CURRENT_TIMESTAMP, 1003, 3, '王五(C)', 3, '王五(C)', 0, 10000.00, 1, 10000.00, 1);

-- B的团队业绩（C的订单）
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10001, 'ORD20240630001', 10000.00, CURRENT_TIMESTAMP, 1003, 3, '王五(C)', 2, '李四(B)', 1, 10000.00, 2, 10000.00, 1);

-- A的团队业绩（C的订单）
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10001, 'ORD20240630001', 10000.00, CURRENT_TIMESTAMP, 1003, 3, '王五(C)', 1, '张三(A)', 2, 10000.00, 2, 10000.00, 1);

-- 场景2：B下单5000元
-- B的个人业绩
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10002, 'ORD20240630002', 5000.00, CURRENT_TIMESTAMP, 1002, 2, '李四(B)', 2, '李四(B)', 0, 5000.00, 1, 5000.00, 1);

-- A的团队业绩（B的订单）
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10002, 'ORD20240630002', 5000.00, CURRENT_TIMESTAMP, 1002, 2, '李四(B)', 1, '张三(A)', 1, 5000.00, 2, 5000.00, 1);

-- 场景3：A下单10000元
-- A的个人业绩
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10003, 'ORD20240630003', 10000.00, CURRENT_TIMESTAMP, 1001, 1, '张三(A)', 1, '张三(A)', 0, 10000.00, 1, 10000.00, 1);

-- 场景4：D下单10000元
-- D的个人业绩
INSERT INTO dms_order_performance_detail (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id, owner_agent_name, target_agent_id, target_agent_name, relation_level, product_amount, performance_type, performance_amount, status)
VALUES (10004, 'ORD20240630004', 10000.00, CURRENT_TIMESTAMP, 1004, 4, '赵六(D)', 4, '赵六(D)', 0, 10000.00, 1, 10000.00, 1);

-- ============================================================
-- 6. 模拟佣金记录数据
-- ============================================================

-- C下单后，直属推荐人B按VIP 30%获得直推奖
INSERT INTO dms_commission_record (tenant_id, rule_version_id, record_no, order_id, order_no, order_amount, order_user_id, order_user_name, agent_id, agent_user_id, agent_name, agent_level, commission_level, bonus_type, commission_rate, commission_amount, status)
VALUES (1, 1, 'COM001', 10001, 'ORD20240630001', 10000.00, 1003, '王五(C)', 2, 1002, '李四(B)', 2, 1, 'DIRECT_REWARD', 0.3000, 3000.00, 0);

-- C下单后，无限层上级A按一星董事5%获得团队分红
INSERT INTO dms_commission_record (tenant_id, rule_version_id, record_no, order_id, order_no, order_amount, order_user_id, order_user_name, agent_id, agent_user_id, agent_name, agent_level, commission_level, bonus_type, commission_rate, commission_amount, status)
VALUES (1, 1, 'COM002', 10001, 'ORD20240630001', 10000.00, 1003, '王五(C)', 1, 1001, '张三(A)', 5, 2, 'DIRECTOR_SHARE', 0.0500, 500.00, 0);

-- B下单后，直属推荐人A按一星董事52%获得直推奖
INSERT INTO dms_commission_record (tenant_id, rule_version_id, record_no, order_id, order_no, order_amount, order_user_id, order_user_name, agent_id, agent_user_id, agent_name, agent_level, commission_level, bonus_type, commission_rate, commission_amount, status)
VALUES (1, 1, 'COM003', 10002, 'ORD20240630002', 5000.00, 1002, '李四(B)', 1, 1001, '张三(A)', 5, 1, 'DIRECT_REWARD', 0.5200, 2600.00, 0);
