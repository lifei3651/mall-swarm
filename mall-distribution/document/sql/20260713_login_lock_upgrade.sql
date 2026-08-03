ALTER TABLE `dms_admin_user`
  ADD COLUMN `failed_login_count` int NOT NULL DEFAULT 0 COMMENT '连续密码错误次数' AFTER `status`,
  ADD COLUMN `lock_time` datetime DEFAULT NULL COMMENT '密码错误锁定时间，非空表示必须人工/重置密码解锁' AFTER `failed_login_count`;

ALTER TABLE `dms_shop_member`
  ADD COLUMN `failed_login_count` int NOT NULL DEFAULT 0 COMMENT '连续密码错误次数' AFTER `status`,
  ADD COLUMN `lock_time` datetime DEFAULT NULL COMMENT '密码错误锁定时间，非空表示必须人工/重置密码解锁' AFTER `failed_login_count`;
