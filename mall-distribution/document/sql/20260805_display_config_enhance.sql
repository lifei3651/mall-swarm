-- 分类表新增首页展示开关
ALTER TABLE `dms_shop_category`
ADD COLUMN `show_on_home` tinyint NOT NULL DEFAULT 1 COMMENT '是否在首页展示：1-展示，0-隐藏' AFTER `status`;

-- display_config表新增布局模板和首页分类开关（如果不存在）
-- 注意：layout_template 和 show_home_categories 已在之前的迁移中添加
-- 这里只补充 show_bottom_category_nav
ALTER TABLE `dms_tenant_display_config`
ADD COLUMN `show_bottom_category_nav` tinyint NOT NULL DEFAULT 1 COMMENT '底部导航是否展示分类入口：1-展示，0-隐藏' AFTER `show_home_categories`;
