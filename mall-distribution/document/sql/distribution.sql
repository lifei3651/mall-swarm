-- ============================================================
-- 分销分佣系统数据库表结构
-- 项目: mall-distribution
-- 创建时间: 2026-06-30
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `mall_distribution` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `mall_distribution`;

-- ============================================================
-- 1. 代理表 (dms_agent)
-- 存储代理基本信息
-- ============================================================
DROP TABLE IF EXISTS `dms_agent`;
CREATE TABLE `dms_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '代理ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID（ums_user表）',
  `agent_code` varchar(32) NOT NULL COMMENT '代理编号（唯一标识）',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '代理名称',
  `agent_level` tinyint NOT NULL DEFAULT 1 COMMENT '卡级：1会员 2VIP 3店铺 4代理 5一星董事 6二星董事 7三星董事 8合伙人',
  `parent_id` bigint DEFAULT NULL COMMENT '直属上级代理ID',
  `ancestor_ids` text DEFAULT NULL COMMENT '所有上级ID路径，不限层',
  `level_depth` int NOT NULL DEFAULT 1 COMMENT '层级深度（1表示顶级代理）',
  `invite_code` varchar(32) NOT NULL COMMENT '邀请码（用于扫码绑定）',
  `qr_code_url` varchar(256) DEFAULT NULL COMMENT '推广二维码URL',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号',
  `bank_name` varchar(64) DEFAULT NULL COMMENT '开户行',
  `bank_account` varchar(32) DEFAULT NULL COMMENT '银行账号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常 2-冻结',
  `source_type` tinyint NOT NULL DEFAULT 1 COMMENT '来源：1-自主注册 2-扫码邀请 3-后台添加 4-批量导入',
  `import_batch_id` varchar(64) DEFAULT NULL COMMENT '导入批次ID（批量导入时记录）',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_agent_code` (`agent_code`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_ancestor_ids` (`ancestor_ids`(100)),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理表';

-- ============================================================
-- 2. 代理关系表 (dms_agent_relation)
-- 支持切线（代理关系变更）
-- ============================================================
DROP TABLE IF EXISTS `dms_agent_relation`;
CREATE TABLE `dms_agent_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `parent_user_id` bigint DEFAULT NULL COMMENT '上级用户ID',
  `parent_agent_id` bigint DEFAULT NULL COMMENT '上级代理ID',
  `relation_level` int NOT NULL DEFAULT 1 COMMENT '关系层级：1-直属 2-二级 3-三级...',
  `relation_path` text DEFAULT NULL COMMENT '关系路径，不限层',
  `is_valid` tinyint NOT NULL DEFAULT 1 COMMENT '是否有效：0-无效（切线后失效） 1-有效',
  `bind_type` tinyint NOT NULL DEFAULT 1 COMMENT '绑定方式：1-扫码绑定 2-邀请码绑定 3-后台绑定 4-导入绑定',
  `bind_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `unbind_time` datetime DEFAULT NULL COMMENT '解绑时间（切线时记录）',
  `unbind_reason` varchar(256) DEFAULT NULL COMMENT '解绑原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_parent_valid` (`user_id`, `parent_user_id`, `is_valid`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_parent_agent_id` (`parent_agent_id`),
  KEY `idx_relation_path` (`relation_path`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理关系表（支持切线）';

-- ============================================================
-- 2.5 整线迁移申请与双人审核
-- ============================================================
DROP TABLE IF EXISTS `dms_line_change_application`;
CREATE TABLE `dms_line_change_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(64) NOT NULL,
  `agent_id` bigint NOT NULL,
  `old_parent_agent_id` bigint DEFAULT NULL,
  `new_parent_agent_id` bigint NOT NULL,
  `reason` varchar(500) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `applicant_id` bigint NOT NULL,
  `applicant_name` varchar(64) NOT NULL,
  `auditor_id` bigint DEFAULT NULL,
  `auditor_name` varchar(64) DEFAULT NULL,
  `audit_remark` varchar(500) DEFAULT NULL,
  `effective_time` datetime NOT NULL,
  `before_snapshot` json NOT NULL,
  `after_snapshot` json DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `execute_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_agent_status` (`agent_id`,`status`), KEY `idx_status_effective` (`status`,`effective_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='整线迁移申请、双人审核与关系快照';

-- ============================================================
-- 3. 租户/客户公司表 (dms_tenant)
-- 每个客户独立配置品牌、主题和产品展示模板
-- ============================================================
DROP TABLE IF EXISTS `dms_tenant`;
CREATE TABLE `dms_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `tenant_code` varchar(64) NOT NULL COMMENT '租户编码',
  `tenant_name` varchar(128) NOT NULL COMMENT '公司名称',
  `brand_name` varchar(128) DEFAULT NULL COMMENT '前端展示品牌名',
  `logo_url` varchar(512) DEFAULT NULL COMMENT 'Logo地址',
  `theme_color` varchar(32) DEFAULT NULL COMMENT '主题色',
  `product_template` varchar(64) DEFAULT 'standard' COMMENT '产品展示模板',
  `company_address` varchar(255) DEFAULT NULL COMMENT '经营地址',
  `unified_social_credit_code` varchar(32) DEFAULT NULL COMMENT '统一社会信用代码',
  `service_phone` varchar(32) DEFAULT NULL COMMENT '客服电话',
  `service_email` varchar(128) DEFAULT NULL COMMENT '客服邮箱',
  `service_hours` varchar(128) DEFAULT NULL COMMENT '客服工作时间',
  `third_party_services` text COMMENT '隐私政策所需的第三方服务清单',
  `icp_number` varchar(128) DEFAULT NULL COMMENT 'ICP备案号',
  `police_record_number` varchar(128) DEFAULT NULL COMMENT '公安备案号',
  `police_record_url` varchar(512) DEFAULT NULL COMMENT '公安备案链接',
  `business_license_url` varchar(512) DEFAULT NULL COMMENT '营业执照图片',
  `show_business_license` tinyint NOT NULL DEFAULT 1 COMMENT '前台是否展示营业执照：0-隐藏 1-展示',
  `user_agreement` longtext COMMENT '用户协议',
  `privacy_policy` longtext COMMENT '隐私政策',
  `after_sale_policy` longtext COMMENT '售后政策',
  `faqs` longtext COMMENT '常见问题JSON',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户/客户公司表';

-- ============================================================
-- 4. 佣金规则版本表 (dms_commission_rule_version)
-- 每个客户只保留一条 NEW_RETAIL_SIMPLE_DEFAULT，用于订单审计快照
-- ============================================================
DROP TABLE IF EXISTS `dms_commission_rule_version`;
CREATE TABLE `dms_commission_rule_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `version_no` varchar(64) NOT NULL COMMENT '版本编号',
  `version_name` varchar(128) NOT NULL COMMENT '版本名称',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
  `effective_time` datetime DEFAULT NULL COMMENT '生效时间',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金规则版本表';

-- ============================================================
-- 6. 前端展示开关配置表 (dms_tenant_display_config)
-- 控制每家公司前端是否展示 PV、业绩、奖金来源、奖金流向等模块
-- ============================================================
DROP TABLE IF EXISTS `dms_tenant_display_config`;
CREATE TABLE `dms_tenant_display_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `show_pv` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示PV',
  `show_team_performance` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示团队业绩',
  `show_bonus_source` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示奖金来源',
  `show_bonus_flow` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示奖金流向',
  `show_profit` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示利润',
  `show_rank` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示排名/职级',
  `show_binary_area` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示双轨/大小区',
  `show_retail_module` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示新零售模块',
  `show_store_module` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示门店模块',
  `show_company_share` tinyint NOT NULL DEFAULT 0 COMMENT '是否展示公司分账',
  `extra_config_json` json DEFAULT NULL COMMENT '扩展展示配置',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端展示开关配置表';

-- ============================================================
-- 8. 商品PV/BV/成本配置表 (dms_product_pv_config)
-- 每个商品/SKU 可配置不同 PV、BV 和产品成本
-- ============================================================
DROP TABLE IF EXISTS `dms_product_pv_config`;
CREATE TABLE `dms_product_pv_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称',
  `sku_name` varchar(256) DEFAULT NULL COMMENT 'SKU名称',
  `pv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件PV',
  `bv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件BV',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件成本',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_product` (`tenant_id`, `product_id`, `sku_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品PV/BV/成本配置表';

-- ============================================================
-- 9. 订单PV明细快照表 (dms_order_pv_detail)
-- 订单计算时固化商品PV/BV/成本，防止后期改配置影响历史订单
-- ============================================================
DROP TABLE IF EXISTS `dms_order_pv_detail`;
CREATE TABLE `dms_order_pv_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) DEFAULT NULL COMMENT '商品名称',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '实付金额',
  `pv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件PV',
  `total_pv` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总PV',
  `bv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件BV',
  `total_bv` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总BV',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件成本',
  `total_cost` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总成本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_order` (`tenant_id`, `order_id`),
  KEY `idx_product` (`product_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单PV明细快照表';

-- ============================================================
-- 9.5 订单组织归属快照：支付时冻结，后续移线不得修改
-- ============================================================
DROP TABLE IF EXISTS `dms_order_relation_snapshot`;
CREATE TABLE `dms_order_relation_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `rule_version_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `order_user_id` bigint NOT NULL,
  `owner_agent_id` bigint NOT NULL,
  `target_agent_id` bigint NOT NULL,
  `target_user_id` bigint NOT NULL,
  `target_agent_name` varchar(64) DEFAULT NULL,
  `relation_level` int NOT NULL,
  `relation_path` text DEFAULT NULL,
  `snapshot_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_target_level` (`order_id`,`target_agent_id`,`relation_level`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_target_agent` (`target_agent_id`),
  KEY `idx_rule_version` (`rule_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单支付时组织归属快照（不可修改）';

-- ============================================================
-- 10. 奖金计算快照表 (dms_bonus_calculation_snapshot)
-- 保存每次计算的输入、结果、PV、奖金合计和风控状态
-- ============================================================
DROP TABLE IF EXISTS `dms_bonus_calculation_snapshot`;
CREATE TABLE `dms_bonus_calculation_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `rule_version_id` bigint DEFAULT NULL COMMENT '规则版本ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `input_json` json DEFAULT NULL COMMENT '计算输入',
  `result_json` json DEFAULT NULL COMMENT '计算结果',
  `total_pv` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总PV',
  `total_bonus` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总奖金',
  `risk_status` varchar(32) DEFAULT NULL COMMENT '风控状态：PASS/WARN/BLOCK',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_order` (`tenant_id`, `order_id`),
  KEY `idx_rule_version` (`rule_version_id`),
  KEY `idx_risk_status` (`risk_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖金计算快照表';

-- ============================================================
-- 11. 奖金异步计算任务表 (dms_bonus_calculation_task)
-- 订单完成后先入队，后台定时异步计算奖金和风控快照
-- ============================================================
DROP TABLE IF EXISTS `dms_bonus_calculation_task`;
CREATE TABLE `dms_bonus_calculation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `rule_version_id` bigint DEFAULT NULL COMMENT '规则版本ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `order_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '订单实付金额',
  `order_user_id` bigint NOT NULL COMMENT '下单用户ID',
  `order_user_name` varchar(64) DEFAULT NULL COMMENT '下单用户名称',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-成功 3-失败',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `max_retry_count` int NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始处理时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`),
  KEY `idx_tenant_order` (`tenant_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖金异步计算任务表';

-- ============================================================
-- 4. 佣金记录表 (dms_commission_record)
-- 每笔订单产生的分佣记录
-- ============================================================
DROP TABLE IF EXISTS `dms_commission_record`;
CREATE TABLE `dms_commission_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '客户公司ID',
  `rule_version_id` bigint DEFAULT NULL COMMENT '固定奖金版本ID',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号（唯一）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `order_amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `order_user_id` bigint NOT NULL COMMENT '下单用户ID',
  `order_user_name` varchar(64) DEFAULT NULL COMMENT '下单用户名称',
  `agent_id` bigint NOT NULL COMMENT '获得佣金的代理ID',
  `agent_user_id` bigint NOT NULL COMMENT '代理用户ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '代理名称',
  `agent_level` tinyint NOT NULL COMMENT '代理等级',
  `commission_level` int NOT NULL COMMENT '与下单人的关系深度（不限层）',
  `bonus_type` varchar(32) NOT NULL COMMENT 'DIRECT_REWARD或DIRECTOR_SHARE',
  `commission_rate` decimal(5,4) NOT NULL COMMENT '佣金比例',
  `commission_amount` decimal(10,2) NOT NULL COMMENT '佣金金额',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待结算 1-已结算 2-已取消 3-已退款',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `cancel_reason` varchar(256) DEFAULT NULL COMMENT '取消原因',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_no` (`record_no`),
  UNIQUE KEY `uk_order_agent_bonus` (`order_id`, `agent_id`, `bonus_type`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金记录表';

-- ============================================================
-- 5. 代理账户表 (dms_agent_account)
-- 代理的佣金账户信息
-- ============================================================
DROP TABLE IF EXISTS `dms_agent_account`;
CREATE TABLE `dms_agent_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `total_commission` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '累计佣金',
  `settled_commission` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '已结算佣金',
  `unsettled_commission` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '待结算佣金',
  `frozen_commission` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '冻结佣金',
  `withdrawn_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '已提现金额',
  `available_balance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '可提现余额',
  `total_orders` int NOT NULL DEFAULT 0 COMMENT '本人及无限层团队累计有效商品件数',
  `total_team_members` int NOT NULL DEFAULT 0 COMMENT '团队成员数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理账户表';

-- ============================================================
-- 6. 提现记录表 (dms_withdraw_record)
-- 代理提现申请记录
-- ============================================================
DROP TABLE IF EXISTS `dms_withdraw_record`;
CREATE TABLE `dms_withdraw_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `withdraw_no` varchar(64) NOT NULL COMMENT '提现单号',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `withdraw_amount` decimal(10,2) NOT NULL COMMENT '提现金额',
  `withdraw_type` tinyint NOT NULL DEFAULT 1 COMMENT '提现方式：1-银行卡 2-微信 3-支付宝',
  `bank_name` varchar(64) DEFAULT NULL COMMENT '银行名称',
  `bank_account` varchar(32) DEFAULT NULL COMMENT '银行账号',
  `account_name` varchar(64) DEFAULT NULL COMMENT '账户姓名',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-审核通过 2-打款中 3-打款成功 4-审核拒绝',
  `audit_user_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(256) DEFAULT NULL COMMENT '审核备注',
  `pay_time` datetime DEFAULT NULL COMMENT '打款时间',
  `pay_no` varchar(64) DEFAULT NULL COMMENT '打款流水号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现记录表';

-- ============================================================
-- 7. 代理变更日志表 (dms_agent_change_log)
-- 记录代理切线、升级等变更操作
-- ============================================================
DROP TABLE IF EXISTS `dms_agent_change_log`;
CREATE TABLE `dms_agent_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `change_type` tinyint NOT NULL COMMENT '变更类型：1-切线 2-升级 3-降级 4-冻结 5-解冻 6-信息变更',
  `old_parent_agent_id` bigint DEFAULT NULL COMMENT '原上级代理ID',
  `old_parent_name` varchar(64) DEFAULT NULL COMMENT '原上级名称',
  `new_parent_agent_id` bigint DEFAULT NULL COMMENT '新上级代理ID',
  `new_parent_name` varchar(64) DEFAULT NULL COMMENT '新上级名称',
  `old_level` tinyint DEFAULT NULL COMMENT '原等级',
  `new_level` tinyint DEFAULT NULL COMMENT '新等级',
  `change_reason` varchar(256) DEFAULT NULL COMMENT '变更原因',
  `change_detail` text DEFAULT NULL COMMENT '变更详情JSON',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人名称',
  `operator_type` tinyint NOT NULL DEFAULT 1 COMMENT '操作人类型：1-系统 2-管理员 3-代理自己',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_change_type` (`change_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理变更日志表';

-- ============================================================
-- 8. 导入批次表 (dms_import_batch)
-- 批量导入代理/订单的批次记录
-- ============================================================
DROP TABLE IF EXISTS `dms_import_batch`;
CREATE TABLE `dms_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `batch_no` varchar(64) NOT NULL COMMENT '批次编号',
  `batch_name` varchar(128) DEFAULT NULL COMMENT '批次名称',
  `import_type` tinyint NOT NULL COMMENT '导入类型：1-代理导入 2-订单导入 3-关系导入',
  `file_name` varchar(256) DEFAULT NULL COMMENT '导入文件名',
  `file_url` varchar(512) DEFAULT NULL COMMENT '文件存储路径',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总记录数',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功数',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '失败数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-处理完成 3-处理失败',
  `error_file_url` varchar(512) DEFAULT NULL COMMENT '错误文件路径',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人名称',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_import_type` (`import_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次表';

-- ============================================================
-- 9. 导入详情表 (dms_import_detail)
-- 批量导入的每一行记录详情
-- ============================================================
DROP TABLE IF EXISTS `dms_import_detail`;
CREATE TABLE `dms_import_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '详情ID',
  `batch_id` bigint NOT NULL COMMENT '批次ID',
  `batch_no` varchar(64) NOT NULL COMMENT '批次编号',
  `row_num` int NOT NULL COMMENT '行号',
  `raw_data` text DEFAULT NULL COMMENT '原始数据JSON',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待处理 1-成功 2-失败',
  `error_msg` varchar(512) DEFAULT NULL COMMENT '错误信息',
  `target_id` bigint DEFAULT NULL COMMENT '生成的目标ID（代理ID/订单ID等）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入详情表';

-- ============================================================
-- 10. 订单业绩明细表 (dms_order_performance_detail)
-- 记录每笔订单的业绩归因，支持追溯来源
-- ============================================================
DROP TABLE IF EXISTS `dms_order_performance_detail`;
CREATE TABLE `dms_order_performance_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `order_amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `order_time` datetime NOT NULL COMMENT '下单时间',

  -- 订单归属人（实际产生业绩的人）
  `owner_user_id` bigint NOT NULL COMMENT '订单归属用户ID（谁卖的）',
  `owner_agent_id` bigint DEFAULT NULL COMMENT '归属代理ID',
  `owner_agent_name` varchar(64) DEFAULT NULL COMMENT '归属代理名称',

  -- 业绩归因到上级（业绩要累加到谁身上）
  `target_agent_id` bigint NOT NULL COMMENT '目标代理ID（业绩累加到谁）',
  `target_agent_name` varchar(64) DEFAULT NULL COMMENT '目标代理名称',
  `relation_level` int NOT NULL COMMENT '关系深度：0-自己，1-直属，团队不限层',

  -- 商品信息
  `product_id` bigint DEFAULT NULL COMMENT '商品ID',
  `product_name` varchar(256) DEFAULT NULL COMMENT '商品名称',
  `product_category_id` bigint DEFAULT NULL COMMENT '商品分类ID',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `product_amount` decimal(10,2) NOT NULL COMMENT '商品金额',

  -- 业绩统计维度
  `performance_type` tinyint NOT NULL DEFAULT 1 COMMENT '业绩类型：1-个人业绩 2-团队业绩',
  `performance_amount` decimal(10,2) NOT NULL COMMENT '业绩金额',

  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-无效 1-有效 2-退款',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_owner_user_id` (`owner_user_id`),
  KEY `idx_owner_agent_id` (`owner_agent_id`),
  KEY `idx_target_agent_id` (`target_agent_id`),
  KEY `idx_order_time` (`order_time`),
  KEY `idx_performance_type` (`performance_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单业绩明细表（可追溯来源）';

-- ============================================================
-- 11. 代理业绩汇总表 (dms_agent_performance_summary)
-- 按时间维度汇总代理的业绩
-- ============================================================
DROP TABLE IF EXISTS `dms_agent_performance_summary`;
CREATE TABLE `dms_agent_performance_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '代理名称',

  -- 统计维度
  `stat_date` date NOT NULL COMMENT '统计日期',
  `stat_type` tinyint NOT NULL COMMENT '统计类型：1-日 2-周 3-月 4-年',

  -- 个人业绩
  `personal_order_count` int NOT NULL DEFAULT 0 COMMENT '个人有效商品件数',
  `personal_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '个人业绩',

  -- 团队业绩（含自己）
  `team_order_count` int NOT NULL DEFAULT 0 COMMENT '无限层团队有效商品件数',
  `team_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '团队总业绩',

  -- 分层级团队业绩
  `level1_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '一级业绩（直属下级）',
  `level2_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '二级业绩',
  `level3_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '三级业绩',

  -- 团队人数
  `team_member_count` int NOT NULL DEFAULT 0 COMMENT '团队总人数',
  `level1_member_count` int NOT NULL DEFAULT 0 COMMENT '一级人数（直属）',
  `level2_member_count` int NOT NULL DEFAULT 0 COMMENT '二级人数',
  `level3_member_count` int NOT NULL DEFAULT 0 COMMENT '三级人数',

  -- 活跃人数
  `active_member_count` int NOT NULL DEFAULT 0 COMMENT '活跃人数（有订单）',

  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_date_type` (`agent_id`, `stat_date`, `stat_type`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_stat_type` (`stat_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理业绩汇总表';

-- ============================================================
-- 12. 下属业绩贡献表 (dms_subordinate_contribution)
-- 记录每个下属对上级的业绩贡献
-- ============================================================
DROP TABLE IF EXISTS `dms_subordinate_contribution`;
CREATE TABLE `dms_subordinate_contribution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` bigint NOT NULL COMMENT '代理ID（被贡献者）',
  `subordinate_agent_id` bigint NOT NULL COMMENT '下属代理ID（贡献者）',
  `subordinate_user_id` bigint NOT NULL COMMENT '下属用户ID',
  `subordinate_name` varchar(64) DEFAULT NULL COMMENT '下属名称',
  `relation_level` int NOT NULL COMMENT '与下属的关系深度，不限层',

  -- 统计维度
  `stat_date` date NOT NULL COMMENT '统计日期',
  `stat_type` tinyint NOT NULL COMMENT '统计类型：1-日 2-周 3-月 4-年',

  -- 贡献业绩
  `contribution_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '贡献业绩金额',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '贡献有效商品件数',
  `self_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '下属自己的业绩',
  `team_performance` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '下属团队的业绩（不含下属自己）',

  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_sub_date_type` (`agent_id`, `subordinate_agent_id`, `stat_date`, `stat_type`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_subordinate_agent_id` (`subordinate_agent_id`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下属业绩贡献表';

-- ============================================================
-- 13. 业绩排名表 (dms_performance_ranking)
-- 代理业绩排名统计
-- ============================================================
DROP TABLE IF EXISTS `dms_performance_ranking`;
CREATE TABLE `dms_performance_ranking` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '代理名称',
  `agent_level` tinyint NOT NULL COMMENT '代理等级',

  `rank_type` tinyint NOT NULL COMMENT '排名类型：1-个人业绩 2-团队业绩 3-新增代理',
  `rank_period` tinyint NOT NULL COMMENT '排名周期：1-日 2-周 3-月 4-年',
  `stat_date` date NOT NULL COMMENT '统计日期',

  `performance_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '业绩值',
  `ranking` int NOT NULL DEFAULT 0 COMMENT '排名',

  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rank_type_period_date_agent` (`rank_type`, `rank_period`, `stat_date`, `agent_id`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_ranking` (`ranking`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业绩排名表';

-- ============================================================
-- 14. 分销设置表 (dms_distribution_setting)
-- 分销模块总开关配置
-- ============================================================
DROP TABLE IF EXISTS `dms_distribution_setting`;
CREATE TABLE `dms_distribution_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `setting_key` varchar(64) NOT NULL COMMENT '配置键',
  `setting_value` varchar(64) NOT NULL COMMENT '配置值',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销设置表';

INSERT INTO `dms_distribution_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('TEAM_PERFORMANCE_VISIBLE_ALL', 'true', '团队业绩是否默认所有代理可见');
INSERT INTO `dms_distribution_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('DIRECT_SALES_MODE', 'true', '直销累计模式开关');

-- ============================================================
-- 15. 业绩查看白名单表 (dms_performance_view_permission)
-- 当总开关关闭时，仅白名单账号可查看团队业绩
-- ============================================================
DROP TABLE IF EXISTS `dms_performance_view_permission`;
CREATE TABLE `dms_performance_view_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` bigint DEFAULT NULL COMMENT '代理ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '代理名称',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：0-否 1-是',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业绩查看白名单表';

-- ============================================================
-- 15.2 会员资产余额表 (dms_member_asset_account)
-- 当前模式唯一资产为余额，asset_code 固定为 CASH_BONUS
-- ============================================================
DROP TABLE IF EXISTS `dms_member_asset_account`;
CREATE TABLE `dms_member_asset_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` bigint DEFAULT NULL COMMENT '代理ID（未进入奖金体系时为空）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `asset_code` varchar(64) NOT NULL COMMENT '资产编码',
  `asset_name` varchar(128) NOT NULL COMMENT '资产名称',
  `balance` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '可用余额',
  `frozen_balance` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '冻结余额',
  `total_in` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '累计收入',
  `total_out` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '累计支出',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_asset` (`agent_id`, `asset_code`),
  UNIQUE KEY `uk_user_asset` (`user_id`, `asset_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_asset_code` (`asset_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员资产余额表';

-- ============================================================
-- 15.3 会员资产流水表 (dms_member_asset_flow)
-- 记录发放、消费、转出、转入、扣减等资产变动
-- ============================================================
DROP TABLE IF EXISTS `dms_member_asset_flow`;
CREATE TABLE `dms_member_asset_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `flow_no` varchar(64) NOT NULL COMMENT '流水号',
  `agent_id` bigint DEFAULT NULL COMMENT '代理ID（未进入奖金体系时为空）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `related_agent_id` bigint DEFAULT NULL COMMENT '关联代理ID',
  `related_user_id` bigint DEFAULT NULL COMMENT '关联用户ID',
  `asset_code` varchar(64) NOT NULL COMMENT '资产编码',
  `asset_name` varchar(128) NOT NULL COMMENT '资产名称',
  `change_type` tinyint NOT NULL COMMENT '变动类型：1-发放 2-消费 3-转出 4-转入 5-扣减',
  `amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '变动数量',
  `balance_before` decimal(14,2) DEFAULT NULL COMMENT '变动前余额',
  `balance_after` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '变动后余额',
  `operator_id` bigint DEFAULT NULL COMMENT '执行管理员ID；系统流水为0或空',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '执行管理员账号；系统流水为system',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_no` (`flow_no`),
  KEY `idx_agent_asset` (`agent_id`, `asset_code`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员资产流水表';

-- ============================================================
-- 15.3.1 订单商品款真实余额归集表
-- 产品成本与剩余商品款独立于推广奖金，均执行收货后7天保护期和退款冲回
-- ============================================================
DROP TABLE IF EXISTS `dms_order_balance_allocation`;
CREATE TABLE `dms_order_balance_allocation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `allocation_type` varchar(32) NOT NULL COMMENT 'PRODUCT_COST-产品成本 REMAINDER-剩余商品款',
  `target_member_id` bigint NOT NULL COMMENT '目标会员主键',
  `target_user_id` bigint NOT NULL COMMENT '目标用户ID',
  `target_agent_id` bigint NOT NULL COMMENT '目标会员体系ID',
  `original_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '订单初始应归集金额',
  `current_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '退款后当前应归集净额',
  `settled_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '累计已进入余额金额',
  `reversed_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '退款累计冲回金额',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待结算 1-已结算 2-已全部冲回/无需结算',
  `settle_time` datetime DEFAULT NULL COMMENT '首次结算时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_type` (`order_id`, `allocation_type`),
  KEY `idx_status_settle` (`status`, `settle_time`),
  KEY `idx_target_agent` (`target_agent_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品款真实余额归集表';

-- ============================================================
-- 15.3.2 月度佣金结算批次与锁定明细
-- ============================================================
DROP TABLE IF EXISTS `dms_commission_settlement_item`;
DROP TABLE IF EXISTS `dms_commission_settlement_batch`;
CREATE TABLE `dms_commission_settlement_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) NOT NULL,
  `period_start` datetime NOT NULL,
  `period_end` datetime NOT NULL,
  `cutoff_time` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `record_count` int NOT NULL DEFAULT 0,
  `total_amount` decimal(18,2) NOT NULL DEFAULT 0,
  `settled_count` int NOT NULL DEFAULT 0,
  `skipped_count` int NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  `creator_id` bigint NOT NULL,
  `creator_name` varchar(64) NOT NULL,
  `executor_id` bigint DEFAULT NULL,
  `executor_name` varchar(64) DEFAULT NULL,
  `execute_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_batch_no` (`batch_no`), KEY `idx_status_create` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度佣金结算批次';
CREATE TABLE `dms_commission_settlement_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `commission_record_id` bigint NOT NULL,
  `agent_id` bigint NOT NULL,
  `agent_name` varchar(64) DEFAULT NULL,
  `snapshot_amount` decimal(18,2) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `skip_reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_batch_record` (`batch_id`,`commission_record_id`), KEY `idx_record` (`commission_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度佣金结算批次明细和金额快照';

-- ============================================================
-- 15.5 后台操作日志表 (dms_operation_log)
-- 记录资产、奖金规则、财务等关键后台操作
-- ============================================================
DROP TABLE IF EXISTS `dms_admin_session`;
DROP TABLE IF EXISTS `dms_admin_user`;

CREATE TABLE `dms_admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希',
  `salt` varchar(64) NOT NULL COMMENT '密码盐',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `role_code` varchar(64) NOT NULL DEFAULT 'OPERATOR' COMMENT '角色编码',
  `permissions` text COMMENT '权限码，逗号分隔，* 表示全部',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `failed_login_count` int NOT NULL DEFAULT 0 COMMENT '连续密码错误次数',
  `lock_time` datetime DEFAULT NULL COMMENT '密码错误锁定时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员账号表';

CREATE TABLE `dms_admin_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `token` varchar(128) NOT NULL COMMENT '登录令牌',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0失效 1有效',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员会话表';

DROP TABLE IF EXISTS `dms_operation_log`;
CREATE TABLE `dms_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `module_name` varchar(64) NOT NULL COMMENT '模块',
  `operation_type` varchar(64) NOT NULL COMMENT '操作类型',
  `target_type` varchar(64) DEFAULT NULL COMMENT '对象类型',
  `target_id` varchar(64) DEFAULT NULL COMMENT '对象ID',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人',
  `before_data` text COMMENT '操作前数据',
  `after_data` text COMMENT '操作后数据',
  `remark` varchar(1000) DEFAULT NULL COMMENT '可读操作说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module_name`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作日志表';

INSERT INTO `dms_admin_user`
  (`id`, `username`, `password_hash`, `salt`, `nickname`, `role_code`, `permissions`, `status`)
VALUES
  (1, 'admin', '9caec3496b444e62944109574e4a98a3a1cde7f063c9e1c6c5700576f3ab773f', 'admin-default-salt', '超级管理员', 'SUPER_ADMIN', '*', 1);

-- ============================================================
-- 16. 订单财务审计表 (dms_order_finance)
-- 记录每笔订单支付、成本、奖金拨出、公司分账与利润风险
-- ============================================================
DROP TABLE IF EXISTS `dms_order_finance`;
CREATE TABLE `dms_order_finance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `pay_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '订单实付金额',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '退款金额',
  `net_pay_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '净收入金额',
  `product_cost` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '产品成本',
  `bonus_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '奖金拨出总额',
  `company_share_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '公司分账总额',
  `company_profit` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '公司利润',
  `risk_status` tinyint NOT NULL DEFAULT 0 COMMENT '风险状态：0-正常 1-亏损风险',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_risk_status` (`risk_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单财务审计表';

-- ============================================================
-- 17. 财务退款冲账表 (dms_finance_refund)
-- 记录订单退款及是否追回奖金
-- ============================================================
DROP TABLE IF EXISTS `dms_finance_refund`;
CREATE TABLE `dms_finance_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退款单号',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '退款金额',
  `product_refund_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '商品退款金额（参与业绩和奖金冲减）',
  `freight_refund_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '运费退款金额（不参与业绩和奖金）',
  `refund_quantity` int NOT NULL DEFAULT 0 COMMENT '实际退回商品件数',
  `clawback_bonus` tinyint NOT NULL DEFAULT 1 COMMENT '是否追回奖金：0-否 1-是',
  `reason` varchar(256) DEFAULT NULL COMMENT '退款原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_refund_time` (`refund_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务退款冲账表';

-- ============================================================
-- 18. 佣金追回流水表 (dms_commission_clawback)
-- 记录退款导致的奖金扣回、欠款和待抵扣情况
-- ============================================================
DROP TABLE IF EXISTS `dms_commission_clawback`;
CREATE TABLE `dms_commission_clawback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `refund_id` bigint NOT NULL COMMENT '退款记录ID',
  `commission_record_id` bigint NOT NULL COMMENT '原佣金记录ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `agent_id` bigint NOT NULL COMMENT '代理ID',
  `agent_user_id` bigint DEFAULT NULL COMMENT '代理用户ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '代理名称',
  `original_commission_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '原佣金金额',
  `clawback_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '本次追回金额',
  `deducted_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '已扣回金额',
  `debt_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '欠款待抵扣金额',
  `clawback_type` tinyint NOT NULL DEFAULT 1 COMMENT '追回方式：1-待结算减少 2-可提现扣回 3-欠款待抵扣 4-未来佣金抵扣',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-待处理 1-已完成 2-部分完成',
  `reason` varchar(256) DEFAULT NULL COMMENT '原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_refund_id` (`refund_id`),
  KEY `idx_commission_record_id` (`commission_record_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金追回流水表';

-- ============================================================
-- 19. 财务风险规则表 (dms_finance_risk_rule)
-- 配置奖金拨出率、利润率、亏损订单等预警阈值
-- ============================================================
DROP TABLE IF EXISTS `dms_finance_risk_rule`;
CREATE TABLE `dms_finance_risk_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `threshold_value` decimal(12,4) NOT NULL DEFAULT 0 COMMENT '阈值',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务风险规则表';

INSERT INTO `dms_finance_risk_rule` (`rule_code`, `rule_name`, `threshold_value`, `enabled`, `remark`) VALUES
('BONUS_PAYOUT_RATE_MAX', '奖金拨出率预警阈值', 0.35, 1, '运营预警阈值；正式规则理论硬上限为79%（直推65%+董事分红14%）'),
('PROFIT_RATE_MIN', '利润率下限', 0.10, 1, '单笔及汇总利润率低于该值时预警'),
('LOSS_ORDER_COUNT_MAX', '风险订单数上限', 0, 1, '风险订单数大于该值时预警');

-- ============================================================
-- 17. 订单公司分账表 (dms_order_company_share)
-- 记录每笔订单进入公司不同账号的分账
-- ============================================================
DROP TABLE IF EXISTS `dms_order_company_share`;
CREATE TABLE `dms_order_company_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `account_id` bigint DEFAULT NULL COMMENT '公司收款账号ID',
  `account_name` varchar(64) DEFAULT NULL COMMENT '公司收款账号名称',
  `share_rate` decimal(8,4) DEFAULT NULL COMMENT '分账比例',
  `share_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '分账金额',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_account_id` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单公司分账表';

-- ============================================================
-- 20. 商城会员表 (dms_shop_member)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_member`;
CREATE TABLE `dms_shop_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `login_account` varchar(64) DEFAULT NULL COMMENT '登录账号',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希（BCrypt）',
  `salt` varchar(64) DEFAULT NULL COMMENT '密码盐（旧版SHA-256使用，现已弃用）',
  `pay_password_hash` varchar(128) DEFAULT NULL COMMENT '独立支付密码哈希（BCrypt）',
  `pay_password_failed_count` int NOT NULL DEFAULT 0 COMMENT '连续支付密码错误次数',
  `pay_password_lock_time` datetime DEFAULT NULL COMMENT '支付密码锁定时间',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '头像',
  `invite_code` varchar(8) DEFAULT NULL COMMENT '邀请码（8位大写字母）',
  `inviter_id` bigint DEFAULT NULL COMMENT '邀请人userId',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `system_account` tinyint NOT NULL DEFAULT 0 COMMENT '系统内部资金账户：0-否 1-是',
  `failed_login_count` int NOT NULL DEFAULT 0 COMMENT '连续密码错误次数',
  `lock_time` datetime DEFAULT NULL COMMENT '密码错误锁定时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_login_account` (`login_account`),
  UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城会员表';

-- ============================================================
-- 21. 商城会员会话表 (dms_shop_member_session)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_member_session`;
CREATE TABLE `dms_shop_member_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `token` varchar(128) NOT NULL COMMENT '登录令牌',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-失效 1-有效',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城会员会话表';

-- ============================================================
-- 22. 商城会员收货地址表 (dms_shop_address)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_address`;
CREATE TABLE `dms_shop_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货手机号',
  `province` varchar(64) DEFAULT NULL COMMENT '省',
  `city` varchar(64) DEFAULT NULL COMMENT '市',
  `district` varchar(64) DEFAULT NULL COMMENT '区县',
  `detail_address` varchar(512) NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-删除 1-正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城会员收货地址表';

-- ============================================================
-- 23. 商城分类表 (dms_shop_category)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_category`;
CREATE TABLE `dms_shop_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `category_name` varchar(64) NOT NULL COMMENT '分类名称',
  `icon_url` varchar(512) DEFAULT NULL COMMENT '分类图标',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城分类表';

-- ============================================================
-- 24. 商城首页轮播表 (dms_shop_banner)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_banner`;
CREATE TABLE `dms_shop_banner` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮播ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `image_url` varchar(512) NOT NULL COMMENT '图片地址',
  `link_type` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '跳转类型：NONE/PRODUCT/CATEGORY/URL',
  `link_value` varchar(256) DEFAULT NULL COMMENT '跳转值',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城首页轮播表';

-- ============================================================
-- 25. 商城公告表 (dms_shop_notice)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_notice`;
CREATE TABLE `dms_shop_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` varchar(1000) DEFAULT NULL COMMENT '公告内容',
  `notice_type` tinyint NOT NULL DEFAULT 1 COMMENT '公告类型：1-系统公告 2-活动公告 3-物流公告',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城公告表';

-- ============================================================
-- 25.5 中国省市区运费模板
-- ============================================================
DROP TABLE IF EXISTS `dms_freight_template`;
CREATE TABLE `dms_freight_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `template_name` varchar(128) NOT NULL,
  `default_mode` varchar(32) NOT NULL DEFAULT 'FREE' COMMENT 'FREE/FIXED/UNAVAILABLE',
  `default_freight_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `rules_json` json DEFAULT NULL COMMENT '省市区包邮/加运费/不发货特例',
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_template_name` (`tenant_id`,`template_name`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中国省市区运费模板';

-- ============================================================
-- 26. 商城商品表 (dms_shop_product)
-- 用户端商城展示、下单、PV/成本快照的商品来源
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_product`;
CREATE TABLE `dms_shop_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `product_no` varchar(64) NOT NULL COMMENT '商品编号',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称',
  `subtitle` varchar(512) DEFAULT NULL COMMENT '副标题/卖点',
  `category_name` varchar(64) DEFAULT NULL COMMENT '分类名称',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '主图',
  `gallery_urls` json DEFAULT NULL COMMENT '商品轮播图URL数组',
  `sale_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '销售价',
  `market_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '划线价',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件成本',
  `pv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件PV',
  `bv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件BV',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `sales_count` int NOT NULL DEFAULT 0 COMMENT '销量',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
  `detail` text DEFAULT NULL COMMENT '商品详情',
  `detail_images` json DEFAULT NULL COMMENT '商品详情图URL数组',
  `delivery_address` varchar(255) DEFAULT NULL COMMENT '发货地',
  `delivery_province` varchar(64) DEFAULT NULL COMMENT '发货省',
  `delivery_city` varchar(64) DEFAULT NULL COMMENT '发货市',
  `delivery_district` varchar(64) DEFAULT NULL COMMENT '发货区/县',
  `freight_type` tinyint NOT NULL DEFAULT 0 COMMENT '运费：0包邮 1固定 2满额包邮 3模板',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '固定运费',
  `free_shipping_amount` decimal(12,2) DEFAULT NULL COMMENT '满额包邮门槛',
  `freight_template_name` varchar(128) DEFAULT NULL COMMENT '运费模板名称',
  `freight_template_id` bigint DEFAULT NULL COMMENT '运费模板ID',
  `delivery_time` varchar(64) DEFAULT NULL COMMENT '承诺发货时效',
  `after_sale_policy` varchar(1000) DEFAULT NULL COMMENT '售后政策',
  `service_tags` json DEFAULT NULL COMMENT '服务标签数组',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_no` (`product_no`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_category` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商品表';

-- 外部团队平移的期初历史数据。只用于保留历史和升级基线，不补发历史奖金。
DROP TABLE IF EXISTS `dms_migration_baseline`;
CREATE TABLE `dms_migration_baseline` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) NOT NULL,
  `agent_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `external_member_code` varchar(128) NOT NULL,
  `historical_order_count` int NOT NULL DEFAULT 0,
  `historical_personal_performance` decimal(14,2) NOT NULL DEFAULT 0,
  `historical_team_performance` decimal(14,2) NOT NULL DEFAULT 0,
  `initial_level` tinyint NOT NULL DEFAULT 1,
  `cutover_time` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_migration_agent` (`agent_id`),
  UNIQUE KEY `uk_migration_external_code` (`external_member_code`),
  KEY `idx_migration_batch` (`batch_no`),
  KEY `idx_migration_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部团队平移期初基线';

-- ============================================================
-- 27. 商城SKU表 (dms_shop_sku)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_sku`;
CREATE TABLE `dms_shop_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_no` varchar(64) NOT NULL COMMENT 'SKU编号',
  `sku_name` varchar(128) NOT NULL COMMENT 'SKU名称',
  `attrs_json` json DEFAULT NULL COMMENT '规格属性JSON',
  `image_url` varchar(512) DEFAULT NULL COMMENT 'SKU图片',
  `sale_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '销售价',
  `market_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '划线价',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件成本',
  `pv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件PV',
  `bv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件BV',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `sales_count` int NOT NULL DEFAULT 0 COMMENT '销量',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_no` (`sku_no`),
  KEY `idx_product_status` (`product_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城SKU表';

-- ============================================================
-- 28. 商城前台订单表 (dms_shop_order)
-- 前台用户订单，提交后同步进入业绩、奖金与财务审计
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_order`;
CREATE TABLE `dms_shop_order` (
  `id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '下单用户ID',
  `agent_id` bigint DEFAULT NULL COMMENT '推荐/归属代理ID',
  `invite_code` varchar(32) DEFAULT NULL COMMENT '邀请码',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货手机号',
  `receiver_address` varchar(512) NOT NULL COMMENT '收货地址',
  `receiver_province` varchar(64) DEFAULT NULL COMMENT '收货省',
  `receiver_city` varchar(64) DEFAULT NULL COMMENT '收货市',
  `receiver_district` varchar(64) DEFAULT NULL COMMENT '收货区/县',
  `receiver_detail_address` varchar(512) DEFAULT NULL COMMENT '详细收货地址',
  `total_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '商品总额',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '运费',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '实付金额',
  `total_pv` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总PV',
  `total_cost` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '总成本',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待付款 1-待发货 2-已发货 3-已完成 4-已关闭（取消/超时/整单退款）',
  `pay_type` varchar(32) DEFAULT NULL COMMENT '支付方式',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `delivery_company` varchar(64) DEFAULT NULL COMMENT '物流公司',
  `delivery_no` varchar(64) DEFAULT NULL COMMENT '物流单号',
  `delivery_time` datetime DEFAULT NULL COMMENT '发货时间',
  `receive_time` datetime DEFAULT NULL COMMENT '确认收货时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城前台订单表';

-- ============================================================
-- 28.1 商城订单物流包裹关联表 (dms_shop_order_shipment)
-- 一张订单可拆为多个包裹；同一包裹可关联多张订单（合箱发货）
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_order_shipment`;
CREATE TABLE `dms_shop_order_shipment` (
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

-- ============================================================
-- 29. 商城前台订单明细表 (dms_shop_order_item)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_order_item`;
CREATE TABLE `dms_shop_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称',
  `sku_name` varchar(128) DEFAULT NULL COMMENT 'SKU名称',
  `sku_attrs` json DEFAULT NULL COMMENT '规格属性快照',
  `product_cover` varchar(512) DEFAULT NULL COMMENT '商品主图',
  `price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '成交单价',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `total_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '成交小计',
  `pv_value` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件PV',
  `total_pv` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '小计PV',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '单件成本',
  `total_cost` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '小计成本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城前台订单明细表';

-- ============================================================
-- 29.1 商品真实购买评价 (dms_shop_product_review)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_product_review`;
CREATE TABLE `dms_shop_product_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称快照',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `order_item_id` bigint NOT NULL COMMENT '订单明细ID',
  `user_id` bigint NOT NULL COMMENT '评价会员userId',
  `reviewer_name` varchar(64) NOT NULL COMMENT '前台脱敏评价人名称',
  `reviewer_avatar` varchar(512) DEFAULT NULL COMMENT '评价人头像快照',
  `rating` tinyint NOT NULL COMMENT '1-5星',
  `content` varchar(1000) NOT NULL COMMENT '评价内容',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0隐藏 1展示',
  `hidden_reason` varchar(255) DEFAULT NULL COMMENT '后台隐藏原因',
  `hidden_by` bigint DEFAULT NULL COMMENT '操作管理员ID',
  `hidden_by_name` varchar(64) DEFAULT NULL COMMENT '操作管理员名称',
  `hidden_time` datetime DEFAULT NULL COMMENT '隐藏时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_order_item` (`order_item_id`),
  KEY `idx_review_product_status_time` (`product_id`,`status`,`create_time`),
  KEY `idx_review_user` (`user_id`),
  KEY `idx_review_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品真实购买评价';

-- ============================================================
-- 30. 商城售后表 (dms_shop_after_sale)
-- ============================================================
DROP TABLE IF EXISTS `dms_shop_after_sale`;
CREATE TABLE `dms_shop_after_sale` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '售后ID',
  `after_sale_no` varchar(64) NOT NULL COMMENT '售后单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `user_id` bigint NOT NULL COMMENT '业务用户ID',
  `apply_type` tinyint NOT NULL DEFAULT 1 COMMENT '申请类型：1-退款 2-退货退款',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '退款金额',
  `product_refund_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '商品退款金额',
  `freight_refund_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '运费退款金额',
  `refund_quantity` int NOT NULL DEFAULT 0 COMMENT '实际退回商品件数',
  `reason` varchar(512) DEFAULT NULL COMMENT '原因',
  `proof_images` text DEFAULT NULL COMMENT '凭证图片，逗号分隔',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-通过 2-拒绝',
  `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注',
  `audit_user_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `audit_user_name` varchar(64) DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_no` (`after_sale_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城售后表';

DROP TABLE IF EXISTS `dms_shop_after_sale_item`;
CREATE TABLE `dms_shop_after_sale_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `after_sale_id` bigint NOT NULL COMMENT '售后单ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NOT NULL COMMENT '订单商品明细ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) DEFAULT NULL COMMENT '商品名称快照',
  `sku_name` varchar(256) DEFAULT NULL COMMENT '规格名称快照',
  `refund_quantity` int NOT NULL DEFAULT 0 COMMENT '实际退回件数',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '该明细商品退款金额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_after_sale_id` (`after_sale_id`),
  KEY `idx_order_item_id` (`order_item_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城售后商品明细表';

INSERT IGNORE INTO `dms_tenant` (`id`, `tenant_code`, `tenant_name`, `brand_name`, `theme_color`, `product_template`, `status`, `remark`)
VALUES (1, 'DEFAULT', '商城运营主体', '商城', '#0f766e', 'standard', 1, '默认商城配置');

INSERT IGNORE INTO `dms_tenant_display_config`
(`tenant_id`, `show_pv`, `show_team_performance`, `show_bonus_source`, `show_bonus_flow`, `show_profit`, `show_rank`,
 `show_binary_area`, `show_retail_module`, `show_store_module`, `show_company_share`, `extra_config_json`)
VALUES
(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL);

INSERT INTO `dms_shop_category`
(`tenant_id`, `category_name`, `icon_url`, `sort_order`, `status`, `remark`)
VALUES
(1, '护理套装', NULL, 100, 1, '首页推荐分类'),
(1, '健康生活', NULL, 90, 1, '复购商品分类'),
(1, '尊享套装', NULL, 80, 1, '高客单分类'),
(1, '复购专区', NULL, 70, 1, '轻量复购分类');

INSERT INTO `dms_shop_banner`
(`tenant_id`, `title`, `image_url`, `link_type`, `link_value`, `sort_order`, `status`, `remark`)
VALUES
(1, '商城精选套装', 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=1400&q=80', 'PRODUCT', '1', 100, 1, '首页主轮播'),
(1, '家庭复购活动', 'https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=1400&q=80', 'CATEGORY', '健康生活', 90, 1, '分类活动轮播');

INSERT INTO `dms_shop_notice`
(`tenant_id`, `title`, `content`, `sort_order`, `status`)
VALUES
(1, '内部测试商城已开启', '当前为内部全流程测试环境，正式支付通道完成商户配置后启用；生产环境不开放模拟支付。', 100, 1);

INSERT INTO `dms_shop_product`
(`tenant_id`, `product_no`, `product_name`, `subtitle`, `category_name`, `cover_url`, `sale_price`, `market_price`, `cost_amount`, `pv_value`, `bv_value`, `stock`, `sales_count`, `sort_order`, `status`, `detail`)
VALUES
(1, 'LQ-SPU-001', '轻奢焕活礼盒', '适合新客体验的高转化入门套装', '护理套装', 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=900&q=80', 299.00, 399.00, 118.00, 220.00, 220.00, 500, 32, 100, 1, '包含基础护理组合，适合日常复购和新客体验。'),
(1, 'LQ-SPU-002', '每日能量组合', '家庭囤货装，适合复购与团队活动', '健康生活', 'https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=900&q=80', 198.00, 268.00, 72.00, 150.00, 150.00, 800, 61, 90, 1, '围绕日常健康生活场景设计，适合活动套餐。'),
(1, 'LQ-SPU-003', '高阶尊享套装', '高客单价组合，便于观察利润与奖金拨出', '尊享套装', 'https://images.unsplash.com/photo-1612817288484-6f916006741a?auto=format&fit=crop&w=900&q=80', 699.00, 899.00, 288.00, 520.00, 520.00, 300, 18, 80, 1, '高PV高客单套餐，可用于测试不同奖金制度。'),
(1, 'LQ-SPU-004', '复购补充装', '轻量复购商品，适合日常复购场景', '复购专区', 'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=80', 89.00, 129.00, 31.00, 60.00, 60.00, 1200, 126, 70, 1, '适合复购和活动赠品组合。');

INSERT INTO `dms_shop_sku`
(`product_id`, `sku_no`, `sku_name`, `attrs_json`, `sale_price`, `market_price`, `cost_amount`, `pv_value`, `bv_value`, `stock`, `sales_count`, `status`)
VALUES
(1, 'LQ-SKU-001-A', '标准装', JSON_OBJECT('规格', '标准装'), 299.00, 399.00, 118.00, 220.00, 220.00, 300, 0, 1),
(1, 'LQ-SKU-001-B', '双盒装', JSON_OBJECT('规格', '双盒装'), 568.00, 798.00, 230.00, 420.00, 420.00, 200, 0, 1),
(2, 'LQ-SKU-002-A', '家庭装', JSON_OBJECT('规格', '家庭装'), 198.00, 268.00, 72.00, 150.00, 150.00, 800, 0, 1),
(3, 'LQ-SKU-003-A', '尊享套装', JSON_OBJECT('规格', '尊享套装'), 699.00, 899.00, 288.00, 520.00, 520.00, 300, 0, 1),
(4, 'LQ-SKU-004-A', '补充装', JSON_OBJECT('规格', '补充装'), 89.00, 129.00, 31.00, 60.00, 60.00, 1200, 0, 1);
