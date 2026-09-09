# 会员登录密码最低6位

日期：2026-09-09；修改方：Codex；分支：codex/security-membership-payout；起点：90691a95。

## 需求与边界

用户要求密码设置简化为6位。实现最低6位、最多32位，允许6位数字，保留已有较长密码，不强制历史账号改密。支付密码原有固定6位数字规则不变。常见弱密码、连续/重复字符、与账号/手机号关联的弱密码继续拒绝；不放宽后台管理员自身密码。

| 入口 | 前端 | 后端 |
| --- | --- | --- |
| 注册/首次设置 | H5公开、一体化和小程序最低6位 | ShopRegisterDTO、ShopAccountSetupDTO和MemberPasswordPolicy |
| 修改/找回 | H5与小程序最低6位 | ShopPasswordChangeDTO、ShopPasswordResetDTO和统一策略 |
| 后台新增/重置会员密码 | 会员管理最低6位 | AdminMemberCreateDTO、AdminMemberPasswordResetDTO和原服务策略 |
| 支付密码 | 原6位数字规则保留 | PaymentPasswordDTO及钱包服务不改 |
| 既有密码登录 | 不强迫改密 | 既有密码校验不触发新密码策略迁移 |

## 验证

- 小程序410/410、H5 115/115、后台175/175测试通过。
- 后端9类39项通过：MemberPasswordPolicyTest、MemberPasswordLengthAlignmentTest、CriticalRequestProtectionTest、RequestInputValidationTest、MiniProgramAccountAuthControllerTest、WeChatMiniProgramAuthServiceTest、AdminMemberSecurityServiceTest、PaymentPasswordLockRaceTest、ShopWalletServiceTest。
- 新增5/6/10/32/33位边界、6位数字、弱密码、后台管理员及支付密码规则不变的覆盖；H5增加源代码约束测试，小程序使用页面模拟接口测试。不是线上真实账号验收。
- 小程序同源/工程检查、官方32个WXML与34个WXSS编译通过；公开/团队/一体化H5构建及边界、后台构建通过，构建已有大包警告仍在。
- 未实际设置用户密码、发送短信或调用真实支付渠道；未执行真机密码流程。

## 发布状态

修改前工作区干净、远程pull成功。公开H5只读版本仍1.0.135/c6f72305；其他线上组件沿用版本报告。VERSION和固定正式小程序目录未动，未部署服务器、未上传腾讯、未提审。

本次含后端规则变更，不能仅更新前端：下次发布先部署配套后端，再更新H5、后台会员管理与小程序。旧服务器会拒绝6位登录密码。
