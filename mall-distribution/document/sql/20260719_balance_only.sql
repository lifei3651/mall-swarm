-- 当前新零售模式只保留余额。旧资产数据先归档，再从正式业务表移除。

CREATE TABLE IF NOT EXISTS dms_retired_asset_account_archive LIKE dms_member_asset_account;
INSERT IGNORE INTO dms_retired_asset_account_archive
SELECT * FROM dms_member_asset_account WHERE asset_code <> 'CASH_BONUS';
DELETE FROM dms_member_asset_account WHERE asset_code <> 'CASH_BONUS';

CREATE TABLE IF NOT EXISTS dms_retired_asset_flow_archive LIKE dms_member_asset_flow;
INSERT IGNORE INTO dms_retired_asset_flow_archive
SELECT * FROM dms_member_asset_flow WHERE asset_code <> 'CASH_BONUS';
DELETE FROM dms_member_asset_flow WHERE asset_code <> 'CASH_BONUS';

DROP TABLE IF EXISTS dms_order_asset_payment;
DROP TABLE IF EXISTS dms_asset_type;
