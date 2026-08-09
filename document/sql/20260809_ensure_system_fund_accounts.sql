-- 补齐订单资金分配所需的两个内部归集账户。
-- 安全约束：只按固定负数 user_id 新增或补齐系统账户，不删除、不重建任何会员、订单或余额数据。

START TRANSACTION;

INSERT INTO dms_shop_member
    (user_id, phone, login_account, password_hash, nickname, invite_code,
     inviter_id, status, system_account)
SELECT
    -900000000000000001, 'SYS-REMAINDER-0001', 'SYSTEM_REMAINDER',
    '$2y$12$uau8o8usTBUf58GxRFZGdubD4KaGP9tuO3IKyvm1pFHVkk0aab672',
    '系统剩余金额账户', NULL, NULL, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM dms_shop_member WHERE user_id = -900000000000000001
);

INSERT INTO dms_shop_member
    (user_id, phone, login_account, password_hash, nickname, invite_code,
     inviter_id, status, system_account)
SELECT
    -900000000000000005, 'SYS-COST-0005', 'SYSTEM_PRODUCT_COST',
    '$2y$12$uau8o8usTBUf58GxRFZGdubD4KaGP9tuO3IKyvm1pFHVkk0aab672',
    '系统产品成本账户', NULL, NULL, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM dms_shop_member WHERE user_id = -900000000000000005
);

UPDATE dms_shop_member
SET login_account = 'SYSTEM_REMAINDER', nickname = '系统剩余金额账户',
    status = 0, system_account = 1
WHERE user_id = -900000000000000001;

UPDATE dms_shop_member
SET login_account = 'SYSTEM_PRODUCT_COST', nickname = '系统产品成本账户',
    status = 0, system_account = 1
WHERE user_id = -900000000000000005;

INSERT INTO dms_agent
    (user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids,
     level_depth, invite_code, phone, status, source_type, remark)
SELECT
    -900000000000000001, 'SYS_REMAINDER', '系统剩余金额账户', 1,
    NULL, NULL, 1, 'SYSREM01', NULL, 2, 3, '内部资金归集账户，不属于客户会员'
WHERE NOT EXISTS (
    SELECT 1 FROM dms_agent WHERE user_id = -900000000000000001
);

INSERT INTO dms_agent
    (user_id, agent_code, agent_name, agent_level, parent_id, ancestor_ids,
     level_depth, invite_code, phone, status, source_type, remark)
SELECT
    -900000000000000005, 'SYS_PRODUCT_COST', '系统产品成本账户', 1,
    NULL, NULL, 1, 'SYSCOST1', NULL, 2, 3, '内部资金归集账户，不属于客户会员'
WHERE NOT EXISTS (
    SELECT 1 FROM dms_agent WHERE user_id = -900000000000000005
);

UPDATE dms_agent
SET agent_code = 'SYS_REMAINDER', agent_name = '系统剩余金额账户', status = 2,
    source_type = 3, remark = '内部资金归集账户，不属于客户会员'
WHERE user_id = -900000000000000001;

UPDATE dms_agent
SET agent_code = 'SYS_PRODUCT_COST', agent_name = '系统产品成本账户', status = 2,
    source_type = 3, remark = '内部资金归集账户，不属于客户会员'
WHERE user_id = -900000000000000005;

INSERT INTO dms_agent_account
    (agent_id, user_id, total_commission, settled_commission, unsettled_commission,
     frozen_commission, withdrawn_amount, available_balance, total_orders, total_team_members)
SELECT id, user_id, 0, 0, 0, 0, 0, 0, 0, 0
FROM dms_agent a
WHERE a.user_id IN (-900000000000000001, -900000000000000005)
  AND NOT EXISTS (SELECT 1 FROM dms_agent_account aa WHERE aa.user_id = a.user_id);

INSERT INTO dms_member_asset_account
    (agent_id, user_id, asset_code, asset_name, balance, frozen_balance, total_in, total_out)
SELECT id, user_id, 'CASH_BONUS', '余额', 0, 0, 0, 0
FROM dms_agent a
WHERE a.user_id IN (-900000000000000001, -900000000000000005)
  AND NOT EXISTS (
      SELECT 1 FROM dms_member_asset_account ma
      WHERE ma.user_id = a.user_id AND ma.asset_code = 'CASH_BONUS'
  );

COMMIT;

SELECT user_id, login_account, status, system_account
FROM dms_shop_member
WHERE user_id IN (-900000000000000001, -900000000000000005)
ORDER BY user_id;
