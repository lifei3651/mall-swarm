-- 订单列表展示的服务保障必须来自订单快照，不能在前端硬编码，也不能随商品后续修改而漂移。
-- 历史订单没有该快照，只在迁移时以当时现存商品配置补一次；新订单由下单事务直接写入。

SET @schema_name = DATABASE();
SET @service_tags_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'dms_shop_order_item'
      AND COLUMN_NAME = 'service_tags'
);
SET @sql = IF(
    @service_tags_exists = 0,
    'ALTER TABLE dms_shop_order_item ADD COLUMN service_tags JSON NULL COMMENT ''下单时商品服务保障标签快照'' AFTER product_cover',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE dms_shop_order_item item
INNER JOIN dms_shop_product product ON product.id = item.product_id
SET item.service_tags = product.service_tags
WHERE item.service_tags IS NULL
  AND product.service_tags IS NOT NULL;
