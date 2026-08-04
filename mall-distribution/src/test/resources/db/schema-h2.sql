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
  id_card VARCHAR(18),
  bank_name VARCHAR(64),
  bank_account VARCHAR(32),
  status INT NOT NULL DEFAULT 1,
  source_type INT NOT NULL DEFAULT 1,
  import_batch_id VARCHAR(64),
  remark VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  service_phone VARCHAR(32),
  service_email VARCHAR(128),
  icp_number VARCHAR(128),
  police_record_number VARCHAR(128),
  police_record_url VARCHAR(512),
  business_license_url VARCHAR(512),
  user_agreement CLOB,
  privacy_policy CLOB,
  after_sale_policy CLOB,
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
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_admin_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  salt VARCHAR(64) NOT NULL,
  nickname VARCHAR(64),
  role_code VARCHAR(64) NOT NULL,
  permissions CLOB,
  status INT DEFAULT 1,
  last_login_time TIMESTAMP,
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
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  bank_account VARCHAR(32),
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
  username VARCHAR(64) UNIQUE,
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

CREATE TABLE IF NOT EXISTS dms_shop_product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  product_no VARCHAR(64) NOT NULL UNIQUE,
  product_name VARCHAR(256) NOT NULL,
  subtitle VARCHAR(512),
  category_name VARCHAR(64),
  cover_url VARCHAR(512),
  gallery_urls CLOB,
  sale_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  market_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  bv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  detail CLOB,
  detail_images CLOB,
  delivery_address VARCHAR(255),
  delivery_province VARCHAR(64),
  delivery_city VARCHAR(64),
  delivery_district VARCHAR(64),
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
  bv_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  status INT NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dms_shop_order (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  tenant_id BIGINT NOT NULL DEFAULT 1,
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
  status INT NOT NULL DEFAULT 1,
  pay_type VARCHAR(32),
  remark VARCHAR(512),
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
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  status INT NOT NULL DEFAULT 0,
  audit_remark VARCHAR(512),
  audit_user_id BIGINT,
  audit_user_name VARCHAR(64),
  audit_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
  app_secret VARCHAR(512),
  callback_token VARCHAR(256),
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
