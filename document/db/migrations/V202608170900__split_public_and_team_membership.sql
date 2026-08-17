-- 公开商城账号和团队H5账号共用登录体系，但只有主动加入团队业务的账号参与关系、业绩和奖金。
-- 升级前已存在的账号来自原一体化商城，统一保留其原有团队业务资格；升级后的公开注册默认写入0。
ALTER TABLE `dms_shop_member`
  ADD COLUMN `team_opt_in` tinyint NOT NULL DEFAULT 1 COMMENT '团队业务选择：0-普通购物账号 1-已加入团队业务'
  AFTER `system_account`;

UPDATE `dms_shop_member`
SET `team_opt_in` = 1
WHERE `system_account` = 0;
