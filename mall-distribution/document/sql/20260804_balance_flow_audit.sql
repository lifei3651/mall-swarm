-- 补齐余额流水审计信息：变动前余额及实际执行管理员。
-- 历史流水无法还原管理员时保留 NULL，后台显示“历史记录未留存”。
ALTER TABLE dms_member_asset_flow
  ADD COLUMN balance_before decimal(14,2) DEFAULT NULL COMMENT '变动前余额' AFTER amount,
  ADD COLUMN operator_id bigint DEFAULT NULL COMMENT '执行管理员ID；系统流水为0或空' AFTER balance_after,
  ADD COLUMN operator_name varchar(64) DEFAULT NULL COMMENT '执行管理员账号；系统流水为system' AFTER operator_id;
