-- 租户表增加营业执照展示开关和常见问题FAQ字段
ALTER TABLE `dms_tenant`
    ADD COLUMN `show_business_license` tinyint NOT NULL DEFAULT 1 COMMENT '是否在前台展示营业执照：1-展示，0-隐藏' AFTER `business_license_url`,
    ADD COLUMN `faqs` text COMMENT '常见问题FAQ，JSON格式存储' AFTER `after_sale_policy`;
