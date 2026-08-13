-- 客户初始化占位脚本。
-- 首次部署时，基础表结构来自当前 mall-distribution 基线。
-- 此处只建立可启动的默认租户；统一部署入口会安全写入 .env 中的客户名称、品牌色和商品模板。

INSERT IGNORE INTO dms_tenant
    (id, tenant_code, tenant_name, brand_name, theme_color, product_template, status, remark)
VALUES
    (1, 'default', '客户公司名称', '客户商城名称', '#0f766e', 'standard', 1, '私有化部署默认客户');

INSERT IGNORE INTO dms_tenant_display_config
    (tenant_id, show_pv, show_team_performance, show_bonus_source, show_bonus_flow, show_profit, show_rank,
     show_binary_area, show_retail_module, show_store_module, show_company_share)
VALUES
    (1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
