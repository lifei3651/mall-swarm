-- 商城订单多包裹/合箱发货升级
-- 兼容规则：dms_shop_order 原物流字段保留第一件包裹，旧接口和历史数据不受影响。

CREATE TABLE IF NOT EXISTS `dms_shop_order_shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号快照',
  `delivery_company` varchar(64) NOT NULL COMMENT '物流公司',
  `delivery_no` varchar(64) NOT NULL COMMENT '物流单号',
  `shipment_quantity` int NOT NULL DEFAULT 1 COMMENT '本包裹对应该订单的发货件数',
  `source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/EXCEL_IMPORT/ERP',
  `delivery_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发货时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_shipment` (`tenant_id`, `order_id`, `delivery_company`, `delivery_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_tracking` (`tenant_id`, `delivery_company`, `delivery_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单物流包裹关联表';

-- 若表由较早版本创建，补充发货数量字段；本段可重复执行。
SET @shipment_quantity_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'dms_shop_order_shipment'
    AND column_name = 'shipment_quantity'
);
SET @shipment_quantity_ddl = IF(
  @shipment_quantity_exists = 0,
  'ALTER TABLE `dms_shop_order_shipment` ADD COLUMN `shipment_quantity` int NOT NULL DEFAULT 1 COMMENT ''本包裹对应该订单的发货件数'' AFTER `delivery_no`',
  'SELECT 1'
);
PREPARE shipment_quantity_stmt FROM @shipment_quantity_ddl;
EXECUTE shipment_quantity_stmt;
DEALLOCATE PREPARE shipment_quantity_stmt;

-- 把历史订单已有的单物流字段补成第一条包裹记录，可重复安全执行。
INSERT INTO `dms_shop_order_shipment`
(`tenant_id`, `order_id`, `order_no`, `delivery_company`, `delivery_no`, `shipment_quantity`, `source`, `delivery_time`)
SELECT COALESCE(o.`tenant_id`, 1), o.`id`, o.`order_no`, o.`delivery_company`, o.`delivery_no`,
       COALESCE(items.`total_quantity`, 1), 'LEGACY',
       COALESCE(o.`delivery_time`, o.`update_time`, o.`create_time`, CURRENT_TIMESTAMP)
FROM `dms_shop_order` o
LEFT JOIN (
  SELECT `order_id`, SUM(`quantity`) AS `total_quantity`
  FROM `dms_shop_order_item`
  GROUP BY `order_id`
) items ON items.`order_id` = o.`id`
WHERE o.`delivery_company` IS NOT NULL AND TRIM(o.`delivery_company`) <> ''
  AND o.`delivery_no` IS NOT NULL AND TRIM(o.`delivery_no`) <> ''
ON DUPLICATE KEY UPDATE
  `order_no` = VALUES(`order_no`),
  `shipment_quantity` = GREATEST(`shipment_quantity`, VALUES(`shipment_quantity`));
