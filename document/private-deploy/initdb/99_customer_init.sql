-- 客户初始化占位脚本。
-- 首次部署时，基础表结构来自当前 mall-distribution 基线。
-- 此处只建立可启动的默认租户与安全关闭的奖金程序；统一部署入口会安全写入 .env 中的客户名称、品牌色和商品模板。

INSERT IGNORE INTO dms_tenant
    (id, tenant_code, tenant_name, brand_name, theme_color, product_template, promotion_join_mode, status, remark)
VALUES
    (1, 'default', '客户公司名称', '客户商城名称', '#0f766e', 'standard', 'DISABLED', 1, '私有化部署默认客户');

INSERT IGNORE INTO dms_tenant_display_config
    (tenant_id, show_pv, show_team_performance, show_bonus_source, show_bonus_flow, show_profit, show_rank,
     show_binary_area, show_retail_module, show_store_module, show_company_share)
VALUES
    (1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- distribution.sql 保留了一组便于本地开发的演示目录、轮播、公告和商品。
-- 私有交付第一次初始化时将其全部转为后台草稿，避免客户域名直接公开测试内容。
-- 条件只命中完全没有奖金版本的全新基座；客户后续自行配置的数据不会被重复执行覆盖。
UPDATE dms_tenant_display_config
SET show_pv = 0,
    show_team_performance = 0,
    show_bonus_source = 0,
    show_bonus_flow = 0,
    show_profit = 0,
    show_rank = 0,
    show_binary_area = 0,
    show_retail_module = 0,
    show_store_module = 0,
    show_company_share = 0
WHERE tenant_id = 1
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

UPDATE dms_tenant
SET promotion_join_mode = 'DISABLED'
WHERE id = 1
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

UPDATE dms_shop_category
SET status = 0
WHERE tenant_id = 1
  AND category_name IN ('护理套装', '健康生活', '尊享套装', '复购专区')
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

UPDATE dms_shop_banner
SET status = 0
WHERE tenant_id = 1
  AND title IN ('商城精选套装', '家庭复购活动')
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

UPDATE dms_shop_notice
SET status = 0
WHERE tenant_id = 1
  AND title = '内部测试商城已开启'
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

UPDATE dms_shop_product
SET status = 0
WHERE tenant_id = 1
  AND product_no IN ('LQ-SPU-001', 'LQ-SPU-002', 'LQ-SPU-003', 'LQ-SPU-004')
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

UPDATE dms_shop_sku
SET status = 0
WHERE product_id IN (
    SELECT id FROM dms_shop_product
    WHERE tenant_id = 1
      AND product_no IN ('LQ-SPU-001', 'LQ-SPU-002', 'LQ-SPU-003', 'LQ-SPU-004')
)
  AND NOT EXISTS (SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1);

-- 新客户没有完成独立制度开发和全流程验收前，购物、支付和售后可以运行，但不得产生奖金。
-- 最后写入关闭版本，使上面的首次初始化操作即使脚本被误重复执行也不会再触碰客户配置。
INSERT INTO dms_commission_rule_version
    (tenant_id, version_no, version_name, status, effective_time, remark)
SELECT
    1, 'CUSTOMER_BONUS_DISABLED', '客户奖金程序未接入', 1, NOW(),
    '商城基座安全默认值：正常交易不产生奖金，客户制度开发并验收后再替换'
WHERE NOT EXISTS (
    SELECT 1 FROM dms_commission_rule_version WHERE tenant_id = 1
);
