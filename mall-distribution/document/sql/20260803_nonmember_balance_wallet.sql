-- 余额钱包与奖金体系解耦：尚未进入奖金体系的商城账号也可由后台增加/扣减余额。
-- agent_id 改为可空：无奖金体系记录的账号按 user_id 持有余额账户和流水。

ALTER TABLE dms_member_asset_account
  MODIFY COLUMN agent_id bigint DEFAULT NULL COMMENT '代理ID（未进入奖金体系时为空）',
  ADD UNIQUE KEY uk_user_asset (user_id, asset_code);

ALTER TABLE dms_member_asset_flow
  MODIFY COLUMN agent_id bigint DEFAULT NULL COMMENT '代理ID（未进入奖金体系时为空）';
