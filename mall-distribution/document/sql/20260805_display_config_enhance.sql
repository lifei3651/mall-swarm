-- 商城装修配置兼容迁移。
-- 生产环境可能已经执行过其中一部分迁移，因此这里按字段存在性判断，
-- 避免重复执行导致“Duplicate column”或因旧表缺少锚点字段而中断。
SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'dms_shop_category'
      AND column_name = 'show_on_home'
  ),
  'SELECT 1',
  'ALTER TABLE `dms_shop_category` ADD COLUMN `show_on_home` tinyint NOT NULL DEFAULT 1 COMMENT ''是否在首页展示：1-展示，0-隐藏'' AFTER `status`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 只有旧迁移已创建 show_home_categories 时，才补充底部分类入口开关。
SET @sql := IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'dms_tenant_display_config'
      AND column_name = 'show_home_categories'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'dms_tenant_display_config'
      AND column_name = 'show_bottom_category_nav'
  ),
  'ALTER TABLE `dms_tenant_display_config` ADD COLUMN `show_bottom_category_nav` tinyint NOT NULL DEFAULT 1 COMMENT ''底部导航是否展示分类入口：1-展示，0-隐藏'' AFTER `show_home_categories`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
