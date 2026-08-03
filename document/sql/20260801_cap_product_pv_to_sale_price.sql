-- 修复历史商品/SKU中“单件PV超过销售价”的异常配置。
-- 执行前必须先备份 mall_distribution 数据库；本脚本只收紧PV，不修改价格、库存或历史订单快照。

UPDATE dms_shop_product
SET pv_value = CASE
        WHEN COALESCE(pv_value, 0) < 0 THEN 0
        ELSE GREATEST(0, COALESCE(sale_price, 0))
    END,
    update_time = CURRENT_TIMESTAMP
WHERE COALESCE(pv_value, 0) > GREATEST(0, COALESCE(sale_price, 0))
   OR COALESCE(pv_value, 0) < 0;

UPDATE dms_shop_sku
SET pv_value = CASE
        WHEN COALESCE(pv_value, 0) < 0 THEN 0
        ELSE GREATEST(0, COALESCE(sale_price, 0))
    END,
    update_time = CURRENT_TIMESTAMP
WHERE COALESCE(pv_value, 0) > GREATEST(0, COALESCE(sale_price, 0))
   OR COALESCE(pv_value, 0) < 0;
