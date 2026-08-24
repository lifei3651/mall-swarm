-- H2数据库Schema（兼容MySQL模式）

-- 代理表
CREATE TABLE IF NOT EXISTS dms_agent (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  agent_code VARCHAR(32) NOT NULL,
  agent_name VARCHAR(64),
  agent_level INT NOT NULL DEFAULT 1,
  parent_id BIGINT,
  ancestor_ids VARCHAR(500),
  level_depth INT NOT NULL DEFAULT 1,
  invite_code VARCHAR(32) NOT NULL,
  qr_code_url VARCHAR(256),
  phone VARCHAR(20),
  real_name VARCHAR(64),
  id_card VARCHAR(256),
  bank_name VARCHAR(64),
  bank_account VARCHAR(512),
  status INT NOT NULL DEFAULT 1,
  source_type INT NOT NULL DEFAULT 1,
  import_batch_id VARCHAR(64),
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_dms_agent_user_id UNIQUE (user_id),
  CONSTRAINT uk_dms_agent_code UNIQUE (agent_code),
  CONSTRAINT uk_dms_agent_invite_code UNIQUE (invite_code)
);

-- 代理关系表
CREATE TABLE IF NOT EXISTS dms_agent_relation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  agent_id BIGINT NOT NULL,
  parent_user_id BIGINT,
  parent_agent_id BIGINT,
  relation_level INT NOT NULL DEFAULT 1,
  relation_path VARCHAR(500),
  is_valid INT NOT NULL DEFAULT 1,
  bind_type INT NOT NULL DEFAULT 1,
  bind_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  unbind_time TIMESTAMP,
  unbind_reason VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 租户/客户公司表
CREATE TABLE IF NOT EXISTS dms_tenant (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_code VARCHAR(64) NOT NULL,
  tenant_name VARCHAR(128) NOT NULL,
  brand_name VARCHAR(128),
  logo_url VARCHAR(512),
  theme_color VARCHAR(32),
  product_template VARCHAR(64),
  company_address VARCHAR(255),
  unified_social_credit_code VARCHAR(32),
  service_phone VARCHAR(32),
  service_email VARCHAR(128),
  service_hours VARCHAR(128),
  third_party_services CLOB,
  icp_number VARCHAR(128),
  police_record_number VARCHAR(128),
  police_record_url VARCHAR(512),
  business_license_url VARCHAR(512),
  show_business_license INT DEFAULT 1,
  user_agreement CLOB,
  privacy_policy CLOB,
  after_sale_policy CLOB,
  after_sale_window_mode VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  after_sale_window_days INT NOT NULL DEFAULT 7,
  flash_sale_enabled INT NOT NULL DEFAULT 0,
  flash_sale_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'NONE',
  repurchase_mall_enabled INT NOT NULL DEFAULT 0,
  repurchase_eligibility_mode VARCHAR(24) NOT NULL DEFAULT 'PAID_MEMBER',
  repurchase_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'NONE',
  faqs CLOB,
  status INT NOT NULL DEFAULT 1,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 佣金规则版本表
CREATE TABLE IF NOT EXISTS dms_commission_rule_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  version_no VARCHAR(64) NOT NULL,
  version_name VARCHAR(128) NOT NULL,
  status INT NOT NULL DEFAULT 1,
  effective_time TIMESTAMP,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 前端展示开关配置表
CREATE TABLE IF NOT EXISTS dms_line_change_application (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, apply_no VARCHAR(64) NOT NULL UNIQUE,
  agent_id BIGINT NOT NULL, old_parent_agent_id BIGINT, new_parent_agent_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL, status INT NOT NULL DEFAULT 0,
  applicant_id BIGINT NOT NULL, applicant_name VARCHAR(64) NOT NULL,
  auditor_id BIGINT, auditor_name VARCHAR(64), audit_remark VARCHAR(500),
  effective_time TIMESTAMP NOT NULL, before_snapshot CLOB NOT NULL, after_snapshot CLOB,
  audit_time TIMESTAMP, execute_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 前端展示开关配置表
CREATE TABLE IF NOT EXISTS dms_tenant_display_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  show_pv INT NOT NULL DEFAULT 0,
  show_team_performance INT NOT NULL DEFAULT 0,
  show_bonus_source INT NOT NULL DEFAULT 0,
  show_bonus_flow INT NOT NULL DEFAULT 0,
  show_profit INT NOT NULL DEFAULT 0,
  show_rank INT NOT NULL DEFAULT 0,
  show_binary_area INT NOT NULL DEFAULT 0,
  show_retail_module INT NOT NULL DEFAULT 0,
  show_store_module INT NOT NULL DEFAULT 0,
  show_company_share INT NOT NULL DEFAULT 0,
  extra_config_json CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商城客户配置历史版本表
CREATE TABLE IF NOT EXISTS dms_tenant_config_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  version_no VARCHAR(64) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  tenant_snapshot CLOB NOT NULL,
  display_snapshot CLOB NOT NULL,
  operator_id BIGINT NOT NULL DEFAULT 0,
  operator_name VARCHAR(64) NOT NULL DEFAULT 'system',
  source_version_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tenant_id, version_no)
);

-- 会员资产余额表
CREATE TABLE IF NOT EXISTS dms_member_asset_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT,
  user_id BIGINT NOT NULL,
  asset_code VARCHAR(64) NOT NULL,
  asset_name VARCHAR(128) NOT NULL,
  balance DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_balance DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_in DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_out DECIMAL(14,2) NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (agent_id, asset_code),
  UNIQUE (user_id, asset_code)
);

-- 会员资产流水表
CREATE TABLE IF NOT EXISTS dms_member_asset_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  flow_no VARCHAR(64) NOT NULL UNIQUE,
  agent_id BIGINT,
  user_id BIGINT NOT NULL,
  related_agent_id BIGINT,
  related_user_id BIGINT,
  asset_code VARCHAR(64) NOT NULL,
  asset_name VARCHAR(128) NOT NULL,
  change_type INT NOT NULL,
  amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  balance_before DECIMAL(14,2),
  balance_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  operator_id BIGINT,
  operator_name VARCHAR(64),
  biz_type VARCHAR(64),
  biz_id VARCHAR(64),
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 订单商品款真实余额归集（独立于推广奖金）
CREATE TABLE IF NOT EXISTS dms_order_balance_allocation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  allocation_type VARCHAR(32) NOT NULL,
  target_member_id BIGINT NOT NULL,
  target_user_id BIGINT NOT NULL,
  target_agent_id BIGINT NOT NULL,
  original_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  current_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  settled_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  reversed_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 0,
  settle_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(order_id, allocation_type)
);

CREATE TABLE IF NOT EXISTS dms_commission_settlement_batch (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  batch_no VARCHAR(64) NOT NULL UNIQUE,
  period_start TIMESTAMP NOT NULL,
  period_end TIMESTAMP NOT NULL,
  cutoff_time TIMESTAMP NOT NULL,
  status INT NOT NULL DEFAULT 0,
  record_count INT NOT NULL DEFAULT 0,
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  settled_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  remark VARCHAR(500),
  creator_id BIGINT NOT NULL,
  creator_name VARCHAR(64) NOT NULL,
  executor_id BIGINT,
  executor_name VARCHAR(64),
  execute_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_commission_settlement_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  batch_id BIGINT NOT NULL,
  commission_record_id BIGINT NOT NULL,
  agent_id BIGINT NOT NULL,
  agent_name VARCHAR(64),
  snapshot_amount DECIMAL(18,2) NOT NULL,
  status INT NOT NULL DEFAULT 0,
  skip_reason VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(batch_id, commission_record_id)
);

-- 后台操作日志表
CREATE TABLE IF NOT EXISTS dms_operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  module_name VARCHAR(64) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  target_type VARCHAR(64),
  target_id VARCHAR(64),
  operator_id BIGINT,
  operator_name VARCHAR(64),
  before_data CLOB,
  after_data CLOB,
  remark VARCHAR(1000),
  ip_address VARCHAR(64),
  user_agent VARCHAR(500),
  request_id VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_operation_log_create_time
  ON dms_operation_log(create_time, id);

CREATE TABLE IF NOT EXISTS dms_admin_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  salt VARCHAR(64) NOT NULL,
  nickname VARCHAR(64),
  role_code VARCHAR(64) NOT NULL,
  permissions CLOB,
  merchant_id BIGINT,
  status INT DEFAULT 1,
  last_login_time TIMESTAMP,
  failed_login_count INT NOT NULL DEFAULT 0,
  lock_time TIMESTAMP,
  must_change_password INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_admin_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  token VARCHAR(128) NOT NULL UNIQUE,
  status INT DEFAULT 1,
  expire_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商品 PV/BV/成本配置表
CREATE TABLE IF NOT EXISTS dms_product_pv_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT,
  product_name VARCHAR(256) NOT NULL,
  sku_name VARCHAR(256),
  pv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  bv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 订单 PV 明细快照表
CREATE TABLE IF NOT EXISTS dms_order_pv_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT,
  product_name VARCHAR(256),
  quantity INT NOT NULL DEFAULT 1,
  pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  bv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_bv DECIMAL(12,2) NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 奖金计算快照表
CREATE TABLE IF NOT EXISTS dms_bonus_calculation_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  rule_version_id BIGINT,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  input_json CLOB,
  result_json CLOB,
  total_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_bonus DECIMAL(12,2) NOT NULL DEFAULT 0,
  risk_status VARCHAR(32),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 奖金异步计算任务表
CREATE TABLE IF NOT EXISTS dms_bonus_calculation_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  rule_version_id BIGINT,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  order_user_id BIGINT NOT NULL,
  order_user_name VARCHAR(64),
  status INT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  max_retry_count INT NOT NULL DEFAULT 3,
  fail_reason VARCHAR(512),
  next_retry_time TIMESTAMP,
  start_time TIMESTAMP,
  finish_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(order_id)
);

-- 佣金记录表
CREATE TABLE IF NOT EXISTS dms_commission_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  rule_version_id BIGINT,
  record_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_amount DECIMAL(10,2) NOT NULL,
  order_user_id BIGINT NOT NULL,
  order_user_name VARCHAR(64),
  agent_id BIGINT NOT NULL,
  agent_user_id BIGINT NOT NULL,
  agent_name VARCHAR(64),
  agent_level INT NOT NULL,
  commission_level INT NOT NULL,
  bonus_type VARCHAR(32) NOT NULL,
  commission_rate DECIMAL(5,4) NOT NULL,
  commission_amount DECIMAL(10,2) NOT NULL,
  status INT NOT NULL DEFAULT 0,
  settle_time TIMESTAMP,
  cancel_reason VARCHAR(256),
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(record_no),
  UNIQUE(order_id, agent_id, bonus_type)
);

-- 代理账户表
CREATE TABLE IF NOT EXISTS dms_agent_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  total_commission DECIMAL(12,2) NOT NULL DEFAULT 0,
  settled_commission DECIMAL(12,2) NOT NULL DEFAULT 0,
  unsettled_commission DECIMAL(12,2) NOT NULL DEFAULT 0,
  frozen_commission DECIMAL(12,2) NOT NULL DEFAULT 0,
  withdrawn_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  available_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_orders INT NOT NULL DEFAULT 0,
  total_team_members INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 提现记录表
CREATE TABLE IF NOT EXISTS dms_withdraw_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  withdraw_no VARCHAR(64) NOT NULL,
  agent_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  withdraw_amount DECIMAL(10,2) NOT NULL,
  withdraw_type INT NOT NULL DEFAULT 1,
  bank_name VARCHAR(64),
  bank_account VARCHAR(512),
  account_name VARCHAR(64),
  status INT NOT NULL DEFAULT 0,
  audit_user_id BIGINT,
  audit_time TIMESTAMP,
  audit_remark VARCHAR(256),
  pay_time TIMESTAMP,
  pay_no VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 代理变更日志表
CREATE TABLE IF NOT EXISTS dms_agent_change_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  change_type INT NOT NULL,
  old_parent_agent_id BIGINT,
  old_parent_name VARCHAR(64),
  new_parent_agent_id BIGINT,
  new_parent_name VARCHAR(64),
  old_level INT,
  new_level INT,
  change_reason VARCHAR(256),
  change_detail CLOB,
  operator_id BIGINT,
  operator_name VARCHAR(64),
  operator_type INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 订单业绩明细表
CREATE TABLE IF NOT EXISTS dms_order_performance_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_amount DECIMAL(10,2) NOT NULL,
  order_time TIMESTAMP NOT NULL,
  owner_user_id BIGINT NOT NULL,
  owner_agent_id BIGINT,
  owner_agent_name VARCHAR(64),
  target_agent_id BIGINT NOT NULL,
  target_agent_name VARCHAR(64),
  relation_level INT NOT NULL,
  product_id BIGINT,
  product_name VARCHAR(256),
  product_category_id BIGINT,
  quantity INT NOT NULL DEFAULT 1,
  product_amount DECIMAL(10,2) NOT NULL,
  performance_type INT NOT NULL DEFAULT 1,
  performance_amount DECIMAL(10,2) NOT NULL,
  status INT NOT NULL DEFAULT 1,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_perf_ranking_status_time_target
  ON dms_order_performance_detail(status, order_time, target_agent_id, relation_level);
CREATE INDEX IF NOT EXISTS idx_relation_parent_valid_agent
  ON dms_agent_relation(parent_agent_id, is_valid, agent_id);

-- 代理业绩汇总表
CREATE TABLE IF NOT EXISTS dms_agent_performance_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  agent_name VARCHAR(64),
  stat_date DATE NOT NULL,
  stat_type INT NOT NULL,
  personal_order_count INT NOT NULL DEFAULT 0,
  personal_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  team_order_count INT NOT NULL DEFAULT 0,
  team_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  level1_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  level2_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  level3_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  team_member_count INT NOT NULL DEFAULT 0,
  level1_member_count INT NOT NULL DEFAULT 0,
  level2_member_count INT NOT NULL DEFAULT 0,
  level3_member_count INT NOT NULL DEFAULT 0,
  active_member_count INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 下属业绩贡献表
CREATE TABLE IF NOT EXISTS dms_subordinate_contribution (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT NOT NULL,
  subordinate_agent_id BIGINT NOT NULL,
  subordinate_user_id BIGINT NOT NULL,
  subordinate_name VARCHAR(64),
  relation_level INT NOT NULL,
  stat_date DATE NOT NULL,
  stat_type INT NOT NULL,
  contribution_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  order_count INT NOT NULL DEFAULT 0,
  self_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  team_performance DECIMAL(12,2) NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 导入批次表
CREATE TABLE IF NOT EXISTS dms_import_batch (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(64) NOT NULL,
  batch_name VARCHAR(128),
  import_type INT NOT NULL,
  file_name VARCHAR(256),
  file_url VARCHAR(512),
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 0,
  error_file_url VARCHAR(512),
  operator_id BIGINT,
  operator_name VARCHAR(64),
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 导入详情表
CREATE TABLE IF NOT EXISTS dms_import_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_id BIGINT NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  row_num INT NOT NULL,
  raw_data CLOB,
  status INT NOT NULL DEFAULT 0,
  error_msg VARCHAR(512),
  target_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 业绩排名表
CREATE TABLE IF NOT EXISTS dms_performance_ranking (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT NOT NULL,
  agent_name VARCHAR(64),
  agent_level INT NOT NULL,
  rank_type INT NOT NULL,
  rank_period INT NOT NULL,
  stat_date DATE NOT NULL,
  performance_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  ranking INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_distribution_setting (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  setting_key VARCHAR(64) NOT NULL UNIQUE,
  setting_value VARCHAR(64) NOT NULL,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_performance_view_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT,
  user_id BIGINT,
  agent_name VARCHAR(64),
  enabled INT NOT NULL DEFAULT 1,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_order_finance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  order_no VARCHAR(64),
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  net_pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  product_cost DECIMAL(10,2) NOT NULL DEFAULT 0,
  bonus_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  company_share_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  company_profit DECIMAL(10,2) NOT NULL DEFAULT 0,
  risk_status INT NOT NULL DEFAULT 0,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_finance_refund (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64),
  refund_no VARCHAR(64),
  refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  product_refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  freight_refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  refund_quantity INT NOT NULL DEFAULT 0,
  clawback_bonus INT NOT NULL DEFAULT 1,
  reason VARCHAR(256),
  operator_id BIGINT,
  operator_name VARCHAR(64),
  refund_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_commission_clawback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  refund_id BIGINT NOT NULL,
  commission_record_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64),
  agent_id BIGINT NOT NULL,
  agent_user_id BIGINT,
  agent_name VARCHAR(64),
  original_commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  clawback_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  deducted_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  debt_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  clawback_type INT NOT NULL DEFAULT 1,
  status INT NOT NULL DEFAULT 1,
  reason VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_finance_risk_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL UNIQUE,
  rule_name VARCHAR(128) NOT NULL,
  threshold_value DECIMAL(12,4) NOT NULL DEFAULT 0,
  enabled INT NOT NULL DEFAULT 1,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_order_company_share (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64),
  account_id BIGINT,
  account_name VARCHAR(64),
  share_rate DECIMAL(8,4),
  share_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_member (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  phone VARCHAR(20) NOT NULL UNIQUE,
  login_account VARCHAR(64) UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  salt VARCHAR(64),
  pay_password_hash VARCHAR(128),
  pay_password_failed_count INT NOT NULL DEFAULT 0,
  pay_password_lock_time TIMESTAMP,
  nickname VARCHAR(64),
  avatar_url VARCHAR(512),
  invite_code VARCHAR(8) UNIQUE,
  inviter_id BIGINT,
  status INT NOT NULL DEFAULT 1,
  system_account INT NOT NULL DEFAULT 0,
  team_opt_in INT NOT NULL DEFAULT 1,
  failed_login_count INT NOT NULL DEFAULT 0,
  lock_time TIMESTAMP,
  last_login_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_member_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  token VARCHAR(128) NOT NULL UNIQUE,
  status INT NOT NULL DEFAULT 1,
  expire_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_idempotency_record (
  request_key CHAR(64) PRIMARY KEY,
  status TINYINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_address (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(64) NOT NULL,
  receiver_phone VARCHAR(20) NOT NULL,
  province VARCHAR(64),
  city VARCHAR(64),
  district VARCHAR(64),
  detail_address VARCHAR(512) NOT NULL,
  is_default INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  category_name VARCHAR(64) NOT NULL,
  icon_url VARCHAR(512),
  sort_order INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  first_publish_time TIMESTAMP,
  show_on_home INT NOT NULL DEFAULT 1,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_banner (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  title VARCHAR(128) NOT NULL,
  image_url VARCHAR(512) NOT NULL,
  link_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
  link_value VARCHAR(256),
  sort_order INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_notice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  title VARCHAR(128) NOT NULL,
  content VARCHAR(1000),
  notice_type INT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_merchant (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_no VARCHAR(64) NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  legal_entity_name VARCHAR(128),
  unified_social_credit_code VARCHAR(32),
  bank_account_name VARCHAR(128),
  bank_name VARCHAR(128),
  bank_account_no VARCHAR(512),
  invoice_title VARCHAR(128),
  taxpayer_identification_no VARCHAR(32),
  contract_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  required_deposit_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  profile_version INT NOT NULL DEFAULT 1,
  settlement_mode VARCHAR(24) NOT NULL DEFAULT 'COST_PRICE',
  default_settlement_days INT NOT NULL DEFAULT 0,
  account_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  business_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  fulfillment_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  withdrawal_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  settlement_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  deposit_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  audit_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
  exit_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  status INT NOT NULL DEFAULT 1,
  remark VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, merchant_no)
);

CREATE TABLE IF NOT EXISTS dms_merchant_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL UNIQUE,
  pending_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  available_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  deposit_frozen_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  debt_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_merchant_ledger (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  merchant_name VARCHAR(128),
  ledger_no VARCHAR(96) NOT NULL UNIQUE,
  biz_type VARCHAR(32) NOT NULL,
  biz_id VARCHAR(96) NOT NULL,
  summary VARCHAR(256) NOT NULL,
  pending_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  available_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  deposit_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  debt_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  paid_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  pending_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  available_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  deposit_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  debt_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  paid_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  operator_id BIGINT,
  operator_name VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, merchant_id, biz_type, biz_id)
);

CREATE TABLE IF NOT EXISTS dms_merchant_settlement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT,
  quantity INT NOT NULL,
  refunded_quantity INT NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL,
  settlement_amount DECIMAL(14,2) NOT NULL,
  reversed_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  settlement_delay_days INT NOT NULL DEFAULT 0,
  eligible_time TIMESTAMP,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  available_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(order_item_id)
);

CREATE TABLE IF NOT EXISTS dms_merchant_deposit_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  operation_no VARCHAR(64) NOT NULL UNIQUE,
  operation_type VARCHAR(16) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  balance_after DECIMAL(14,2) NOT NULL,
  reason VARCHAR(256) NOT NULL,
  operator_id BIGINT,
  operator_name VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_merchant_withdrawal (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  withdrawal_no VARCHAR(64) NOT NULL UNIQUE,
  request_no VARCHAR(64),
  merchant_id BIGINT NOT NULL,
  merchant_profile_version INT,
  legal_entity_name_snapshot VARCHAR(128),
  bank_account_name_snapshot VARCHAR(128),
  bank_name_snapshot VARCHAR(128),
  bank_account_no_snapshot VARCHAR(512),
  requested_amount DECIMAL(14,2) NOT NULL,
  invoice_required_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  invoice_received_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  invoice_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
  adjustment_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  adjustment_reason VARCHAR(256),
  actual_paid_amount DECIMAL(14,2),
  payment_reference VARCHAR(128),
  payment_voucher_url VARCHAR(512),
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  resume_status VARCHAR(24),
  reject_reason VARCHAR(256),
  operator_id BIGINT,
  operator_name VARCHAR(64),
  apply_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  paid_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_withdrawal_request
  ON dms_merchant_withdrawal(tenant_id, merchant_id, request_no);

CREATE TABLE IF NOT EXISTS dms_merchant_withdrawal_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  withdrawal_id BIGINT NOT NULL,
  withdrawal_no VARCHAR(64) NOT NULL,
  from_status VARCHAR(24),
  to_status VARCHAR(24) NOT NULL,
  remark VARCHAR(500),
  operator_id BIGINT,
  operator_name VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT,
  merchant_name VARCHAR(128),
  product_no VARCHAR(64) NOT NULL UNIQUE,
  product_name VARCHAR(256) NOT NULL,
  subtitle VARCHAR(512),
  category_name VARCHAR(64),
  cover_url VARCHAR(512),
  gallery_urls CLOB,
  sale_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  market_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  settlement_delay_days_override INT,
  pv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  bv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  safety_stock INT NOT NULL DEFAULT 0,
  purchase_limit INT NOT NULL DEFAULT 0,
  normal_sale_enabled INT NOT NULL DEFAULT 1,
  repurchase_sale_enabled INT NOT NULL DEFAULT 0,
  repurchase_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  repurchase_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  repurchase_purchase_limit INT NOT NULL DEFAULT 0,
  enrollment_sale_enabled INT NOT NULL DEFAULT 0,
  team_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'INHERIT',
  sales_count INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  merchant_review_status VARCHAR(16),
  merchant_review_version INT NOT NULL DEFAULT 0,
  merchant_review_remark VARCHAR(500),
  merchant_review_submitted_at TIMESTAMP,
  merchant_reviewed_at TIMESTAMP,
  merchant_reviewer_id BIGINT,
  merchant_reviewer_name VARCHAR(64),
  detail CLOB,
  detail_images CLOB,
  delivery_address VARCHAR(255),
  delivery_province VARCHAR(64),
  delivery_city VARCHAR(64),
  delivery_district VARCHAR(64),
  shipping_address_id BIGINT,
  return_address_id BIGINT,
  freight_type INT NOT NULL DEFAULT 0,
  freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  free_shipping_amount DECIMAL(12,2),
  freight_template_name VARCHAR(128),
  freight_template_id BIGINT,
  delivery_time VARCHAR(64),
  after_sale_policy VARCHAR(1000),
  service_tags CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Keep the in-memory schema compatible when multiple test contexts reuse a named H2 database.
ALTER TABLE dms_shop_product ADD COLUMN IF NOT EXISTS shipping_address_id BIGINT;
ALTER TABLE dms_shop_product ADD COLUMN IF NOT EXISTS return_address_id BIGINT;
ALTER TABLE dms_shop_product ADD COLUMN IF NOT EXISTS first_publish_time TIMESTAMP;

CREATE TABLE IF NOT EXISTS dms_merchant_product_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  product_id BIGINT NOT NULL,
  review_version INT NOT NULL,
  review_type VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  product_no VARCHAR(64),
  product_name VARCHAR(60) NOT NULL,
  sale_price DECIMAL(12,2) NOT NULL,
  settlement_price DECIMAL(12,2) NOT NULL,
  sku_count INT NOT NULL DEFAULT 0,
  product_snapshot CLOB NOT NULL,
  submitter_id BIGINT,
  submitter_name VARCHAR(64),
  submitted_at TIMESTAMP NOT NULL,
  reviewer_id BIGINT,
  reviewer_name VARCHAR(64),
  review_remark VARCHAR(500),
  reviewed_at TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(product_id, review_version)
);

CREATE TABLE IF NOT EXISTS dms_freight_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  template_name VARCHAR(128) NOT NULL,
  default_mode VARCHAR(32) NOT NULL DEFAULT 'FREE',
  default_freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  rules_json CLOB,
  status INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, template_name)
);

CREATE TABLE IF NOT EXISTS dms_migration_baseline (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(64) NOT NULL,
  agent_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  external_member_code VARCHAR(128) NOT NULL UNIQUE,
  historical_order_count INT NOT NULL DEFAULT 0,
  historical_personal_performance DECIMAL(14,2) NOT NULL DEFAULT 0,
  historical_team_performance DECIMAL(14,2) NOT NULL DEFAULT 0,
  initial_level INT NOT NULL DEFAULT 1,
  cutover_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_sku (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  sku_no VARCHAR(64) NOT NULL UNIQUE,
  sku_name VARCHAR(128) NOT NULL,
  attrs_json VARCHAR(1000),
  image_url VARCHAR(512),
  sale_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  market_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  repurchase_price DECIMAL(12,2),
  repurchase_pv DECIMAL(12,2),
  bv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  safety_stock INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_trade (
  id BIGINT PRIMARY KEY,
  trade_no VARCHAR(64) NOT NULL UNIQUE,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  user_id BIGINT NOT NULL,
  pay_type VARCHAR(32) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 0,
  late_refund_flag INT NOT NULL DEFAULT 0,
  pay_time TIMESTAMP,
  close_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_order (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  trade_id BIGINT,
  trade_no VARCHAR(64),
  payment_order_no VARCHAR(64),
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT,
  merchant_name VARCHAR(128),
  user_id BIGINT NOT NULL DEFAULT 0,
  agent_id BIGINT,
  invite_code VARCHAR(32),
  receiver_name VARCHAR(64) NOT NULL,
  receiver_phone VARCHAR(20) NOT NULL,
  receiver_address VARCHAR(512) NOT NULL,
  receiver_province VARCHAR(64),
  receiver_city VARCHAR(64),
  receiver_district VARCHAR(64),
  receiver_detail_address VARCHAR(512),
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  business_type VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
  business_source_id BIGINT,
  source_live_room_id BIGINT,
  status INT NOT NULL DEFAULT 1,
  pay_type VARCHAR(32),
  late_refund_flag INT NOT NULL DEFAULT 0,
  remark VARCHAR(512),
  service_remark VARCHAR(500),
  pay_time TIMESTAMP,
  delivery_company VARCHAR(64),
  delivery_no VARCHAR(64),
  delivery_time TIMESTAMP,
  receive_time TIMESTAMP,
  cancel_time TIMESTAMP,
  close_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_order_shipment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  delivery_company VARCHAR(64) NOT NULL,
  delivery_no VARCHAR(64) NOT NULL,
  shipment_quantity INT NOT NULL DEFAULT 1,
  source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  delivery_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_order_shipment UNIQUE (tenant_id, order_id, delivery_company, delivery_no)
);

CREATE TABLE IF NOT EXISTS dms_shop_order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  merchant_id BIGINT,
  merchant_name VARCHAR(128),
  product_id BIGINT NOT NULL,
  sku_id BIGINT,
  product_name VARCHAR(256) NOT NULL,
  sku_name VARCHAR(128),
  sku_attrs VARCHAR(1000),
  product_cover VARCHAR(512),
  price DECIMAL(12,2) NOT NULL DEFAULT 0,
  quantity INT NOT NULL DEFAULT 1,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  settlement_delay_days INT NOT NULL DEFAULT 0,
  team_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'INHERIT',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_flash_sale_activity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  activity_name VARCHAR(80) NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT,
  flash_price DECIMAL(12,2) NOT NULL,
  flash_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_stock INT NOT NULL,
  available_stock INT NOT NULL,
  per_user_limit INT NOT NULL DEFAULT 1,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  status INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_flash_sale_reservation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  order_id BIGINT,
  order_no VARCHAR(64),
  quantity INT NOT NULL,
  released_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(activity_id, user_id),
  UNIQUE(order_id)
);

CREATE TABLE IF NOT EXISTS dms_live_room (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  title VARCHAR(80) NOT NULL,
  subtitle VARCHAR(160),
  cover_url VARCHAR(2048) NOT NULL,
  anchor_name VARCHAR(60),
  anchor_id BIGINT,
  live_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCT',
  provider_code VARCHAR(24) NOT NULL DEFAULT 'EXTERNAL',
  stream_name VARCHAR(96),
  watch_url VARCHAR(2048),
  comment_enabled INT NOT NULL DEFAULT 1,
  share_enabled INT NOT NULL DEFAULT 1,
  scheduled_start_time TIMESTAMP NOT NULL,
  scheduled_end_time TIMESTAMP,
  actual_start_time TIMESTAMP,
  actual_end_time TIMESTAMP,
  stop_reason VARCHAR(200),
  status INT NOT NULL DEFAULT 0,
  viewer_count INT NOT NULL DEFAULT 0,
  heat_count INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_live_room_product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  live_room_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, live_room_id, product_id)
);

CREATE TABLE IF NOT EXISTS dms_live_anchor (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  member_user_id BIGINT NOT NULL,
  display_name VARCHAR(60) NOT NULL,
  anchor_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCT',
  company_name VARCHAR(120),
  bio VARCHAR(300),
  status INT NOT NULL DEFAULT 1,
  last_live_time TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, member_user_id)
);

CREATE TABLE IF NOT EXISTS dms_live_comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  live_room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(60) NOT NULL,
  content VARCHAR(300) NOT NULL,
  status INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_live_view_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  live_room_id BIGINT NOT NULL,
  visitor_id CHAR(36) NOT NULL,
  user_id BIGINT,
  enter_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_seen_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  duration_seconds INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, live_room_id, visitor_id)
);

CREATE TABLE IF NOT EXISTS dms_live_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  live_room_id BIGINT NOT NULL,
  visitor_id CHAR(36) NOT NULL,
  user_id BIGINT,
  event_type VARCHAR(24) NOT NULL,
  product_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_service_address (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT,
  shared_to_merchants INT NOT NULL DEFAULT 0,
  address_type INT NOT NULL,
  address_label VARCHAR(64),
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  province VARCHAR(64) NOT NULL,
  city VARCHAR(64) NOT NULL,
  district VARCHAR(64) NOT NULL,
  detail_address VARCHAR(255) NOT NULL,
  is_default INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_product_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(256) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_item_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  reviewer_name VARCHAR(64) NOT NULL,
  reviewer_avatar VARCHAR(512),
  rating INT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status INT NOT NULL DEFAULT 1,
  hidden_reason VARCHAR(255),
  hidden_by BIGINT,
  hidden_by_name VARCHAR(64),
  hidden_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_order_relation_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  rule_version_id BIGINT,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_user_id BIGINT NOT NULL,
  owner_agent_id BIGINT NOT NULL,
  target_agent_id BIGINT NOT NULL,
  target_user_id BIGINT NOT NULL,
  target_agent_name VARCHAR(64),
  relation_level INT NOT NULL,
  relation_path VARCHAR(1000),
  snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(order_id, target_agent_id, relation_level)
);

CREATE TABLE IF NOT EXISTS dms_shop_after_sale (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  after_sale_no VARCHAR(64) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  apply_type INT NOT NULL DEFAULT 1,
  refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  product_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  freight_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  refund_quantity INT NOT NULL DEFAULT 0,
  reason VARCHAR(512),
  proof_images CLOB,
  return_address_id BIGINT,
  return_address VARCHAR(512),
  return_delivery_company VARCHAR(64),
  return_delivery_no VARCHAR(128),
  return_shipped_at TIMESTAMP,
  return_received_at TIMESTAMP,
  status INT NOT NULL DEFAULT 0,
  audit_remark VARCHAR(512),
  audit_user_id BIGINT,
  audit_user_name VARCHAR(64),
  audit_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE dms_shop_after_sale ADD COLUMN IF NOT EXISTS return_delivery_company VARCHAR(64);
ALTER TABLE dms_shop_after_sale ADD COLUMN IF NOT EXISTS return_delivery_no VARCHAR(128);
ALTER TABLE dms_shop_after_sale ADD COLUMN IF NOT EXISTS return_shipped_at TIMESTAMP;
ALTER TABLE dms_shop_after_sale ADD COLUMN IF NOT EXISTS return_received_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS dms_shop_after_sale_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  after_sale_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT,
  product_name VARCHAR(256),
  sku_name VARCHAR(256),
  refund_quantity INT NOT NULL DEFAULT 0,
  refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_erp_integration (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  provider_code VARCHAR(32) NOT NULL,
  integration_name VARCHAR(64) NOT NULL,
  enabled INT NOT NULL DEFAULT 0,
  environment VARCHAR(16) NOT NULL DEFAULT 'TEST',
  endpoint VARCHAR(512),
  app_key VARCHAR(256),
  app_secret VARCHAR(4096),
  callback_token VARCHAR(1024),
  remark VARCHAR(512),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tenant_id, provider_code)
);

CREATE TABLE IF NOT EXISTS dms_erp_sync_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_no VARCHAR(64) NOT NULL,
  integration_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  provider_code VARCHAR(32) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_id VARCHAR(64) NOT NULL,
  status INT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time TIMESTAMP,
  request_summary CLOB,
  response_summary CLOB,
  last_error VARCHAR(1024),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (integration_id, biz_type, biz_id)
);
CREATE TABLE IF NOT EXISTS dms_member_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL, event_key VARCHAR(160) NOT NULL, event_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL, title VARCHAR(128) NOT NULL, summary VARCHAR(300) NOT NULL,
    content VARCHAR(1000) NOT NULL, target_type VARCHAR(32) NOT NULL DEFAULT 'NONE', target_id BIGINT,
    target_parent_id BIGINT, is_read TINYINT NOT NULL DEFAULT 0, read_time TIMESTAMP,
    occurred_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_message_event UNIQUE (tenant_id, member_id, event_key)
);
CREATE INDEX IF NOT EXISTS idx_member_message_unread ON dms_member_message(tenant_id, member_id, is_read, id);
CREATE INDEX IF NOT EXISTS idx_member_message_category ON dms_member_message(tenant_id, member_id, category, id);

CREATE TABLE IF NOT EXISTS dms_message_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, event_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL, title_template VARCHAR(128) NOT NULL, summary_template VARCHAR(300) NOT NULL,
    content_template VARCHAR(1000) NOT NULL, enabled TINYINT NOT NULL DEFAULT 1, version INT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_message_template_event UNIQUE (tenant_id, event_type)
);
CREATE TABLE IF NOT EXISTS dms_message_channel_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, event_type VARCHAR(64) NOT NULL,
    in_app_enabled TINYINT NOT NULL DEFAULT 1, sms_enabled TINYINT NOT NULL DEFAULT 0,
    app_push_enabled TINYINT NOT NULL DEFAULT 0, mini_program_enabled TINYINT NOT NULL DEFAULT 0,
    estimated_sms_cost DECIMAL(10,4) NOT NULL DEFAULT 0, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_message_channel_event UNIQUE (tenant_id, event_type)
);
CREATE TABLE IF NOT EXISTS dms_message_delivery_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, message_id BIGINT NOT NULL,
    channel VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL, retry_count INT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(10,4) NOT NULL DEFAULT 0, provider_message_id VARCHAR(128), error_code VARCHAR(64),
    error_message VARCHAR(255), next_retry_time TIMESTAMP, sent_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_message_delivery_channel UNIQUE (tenant_id, message_id, channel)
);

MERGE INTO dms_message_template (tenant_id,event_type,category,title_template,summary_template,content_template,enabled,version) KEY(tenant_id,event_type) VALUES
(1,'ORDER_PAID','ORDER_LOGISTICS','订单支付成功','订单已完成支付，可查看订单详情。','您的订单已完成支付，后续状态请以订单详情为准。',1,1),
(1,'ORDER_CLOSED','ORDER_LOGISTICS','订单已关闭','订单已关闭，可查看订单详情。','您的订单已关闭，具体原因和退款进度请以订单详情为准。',1,1),
(1,'ORDER_SHIPPED','ORDER_LOGISTICS','订单已发货','商家已发货，可查看物流信息。','您的订单已发货，物流信息请登录后查看。',1,1),
(1,'ORDER_RECEIVED','ORDER_LOGISTICS','订单已完成','订单已确认收货。','您的订单已确认收货。',1,1),
(1,'AFTER_SALE_APPLIED','AFTER_SALE_REFUND','售后申请已提交','售后申请已提交，请留意处理进展。','您的售后申请已提交。',1,1),
(1,'AFTER_SALE_UPDATED','AFTER_SALE_REFUND','售后状态有更新','售后申请有新的处理进展。','您的售后申请状态已更新。',1,1),
(1,'REFUND_RESULT','AFTER_SALE_REFUND','退款结果已更新','退款处理结果已更新。','退款处理结果已更新。',1,1),
(1,'WALLET_FLOW','WALLET_FUNDS','钱包有新流水','钱包余额流水已更新。','钱包明细请登录后查看。',1,1),
(1,'WITHDRAW_SUBMITTED','WALLET_FUNDS','提现申请已提交','提现申请已进入审核。','提现详情请登录后查看。',1,1),
(1,'WITHDRAW_AUDITED','WALLET_FUNDS','提现审核已完成','提现申请审核状态已更新。','提现详情请登录后查看。',1,1),
(1,'WITHDRAW_PAID','WALLET_FUNDS','提现打款状态已更新','提现打款状态已更新。','实际到账请以收款渠道为准。',1,1),
(1,'LOGIN_PASSWORD_CHANGED','ACCOUNT_SECURITY','登录密码已修改','账号安全设置发生变化。','如非本人操作，请立即联系平台。',1,1),
(1,'PAY_PASSWORD_CHANGED','ACCOUNT_SECURITY','支付密码已更新','资金安全设置发生变化。','验证码和密码不会进入消息正文。',1,1),
(1,'PHONE_CHANGED','ACCOUNT_SECURITY','登录手机号已更新','账号安全设置发生变化。','完整号码不会进入消息正文。',1,1),
(1,'SERVICE_NOTICE','SERVICE','服务通知','您有一条新的服务通知。','请登录后查看。',1,1);
MERGE INTO dms_message_channel_config (tenant_id,event_type,in_app_enabled,sms_enabled,app_push_enabled,mini_program_enabled,estimated_sms_cost) KEY(tenant_id,event_type)
SELECT 1,event_type,1,0,0,0,0 FROM dms_message_template WHERE tenant_id=1;
