-- 灵启商城正式ICP备案号。仅更新默认租户，不影响其他租户数据。
UPDATE dms_tenant
SET icp_number = '湘ICP备2026028410号-1',
    update_time = CURRENT_TIMESTAMP
WHERE id = 1
  AND (icp_number IS NULL OR icp_number <> '湘ICP备2026028410号-1');
