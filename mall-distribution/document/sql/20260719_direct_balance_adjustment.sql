-- 余额人工调整改为管理员二次确认后立即生效；实际变动保留在余额流水与操作日志中。
CREATE TABLE IF NOT EXISTS dms_retired_asset_adjustment_archive LIKE dms_asset_adjustment_application;
INSERT IGNORE INTO dms_retired_asset_adjustment_archive
SELECT * FROM dms_asset_adjustment_application;
DROP TABLE IF EXISTS dms_asset_adjustment_application;
