-- 通用客服工单闭环：会员咨询/投诉/售后争议/账号问题与后台处理进度。
-- 工单是系统服务能力，不受商城视觉装修开关控制；首次响应超时只用于排队提醒。

CREATE TABLE IF NOT EXISTS dms_shop_service_ticket (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  ticket_no VARCHAR(32) NOT NULL COMMENT '工单编号',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '客户编号',
  merchant_id BIGINT NULL COMMENT '关联订单所属商户；通用/账号工单为空且仅平台可见',
  member_id BIGINT NOT NULL COMMENT '会员主键',
  user_id BIGINT NOT NULL COMMENT '会员用户编号',
  type VARCHAR(32) NOT NULL COMMENT 'CONSULTATION/COMPLAINT/AFTER_SALE_DISPUTE/ACCOUNT/OTHER',
  subject VARCHAR(100) NOT NULL COMMENT '问题标题',
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/WAITING_MEMBER/RESOLVED/CLOSED',
  order_id BIGINT NULL COMMENT '可选关联订单',
  order_no VARCHAR(64) NULL COMMENT '订单号快照',
  after_sale_id BIGINT NULL COMMENT '可选关联售后',
  after_sale_no VARCHAR(64) NULL COMMENT '售后编号快照',
  assigned_admin_id BIGINT NULL COMMENT '最近处理客服',
  assigned_admin_name VARCHAR(64) NULL COMMENT '客服显示名快照',
  last_reply_by VARCHAR(16) NOT NULL COMMENT 'MEMBER/ADMIN',
  last_reply_time DATETIME NOT NULL COMMENT '最近回复时间',
  first_response_deadline DATETIME NOT NULL COMMENT '首次响应目标时间',
  first_response_at DATETIME NULL COMMENT '首次客服响应时间',
  resolved_time DATETIME NULL COMMENT '客服标记解决时间',
  closed_time DATETIME NULL COMMENT '最终关闭时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_service_ticket_no (tenant_id, ticket_no),
  KEY idx_shop_service_ticket_member (tenant_id, member_id, status, last_reply_time),
  KEY idx_shop_service_ticket_merchant (tenant_id, merchant_id, status, first_response_deadline),
  KEY idx_shop_service_ticket_after_sale (tenant_id, after_sale_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员客服工单';

CREATE TABLE IF NOT EXISTS dms_shop_service_ticket_reply (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '客户编号',
  ticket_id BIGINT NOT NULL COMMENT '工单主键',
  sender_type VARCHAR(16) NOT NULL COMMENT 'MEMBER/ADMIN/SYSTEM',
  sender_id BIGINT NULL COMMENT '发送方内部编号',
  sender_name VARCHAR(64) NULL COMMENT '发送方显示名快照',
  content VARCHAR(1000) NOT NULL COMMENT '回复内容',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_shop_service_ticket_reply (tenant_id, ticket_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员客服工单回复';
