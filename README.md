# 灵启商城

灵启商城是由长沙灵启软件开发有限公司持续开发和维护的商城基础平台，面向客户交付普通商城、会员体系、团队关系、奖金结算、钱包提现、订单售后、秒杀、复购、物流与运营后台等能力。

Copyright © 2026 长沙灵启软件开发有限公司。保留所有权利。

## 当前有效工程

- `mall-distribution`：商城、会员、订单、奖金、钱包、售后和运营后台核心后端。
- `mall-common`：通用接口、安全、日志、缓存和基础能力。
- `mall-mbg`：数据库访问与代码生成配置。
- `mall-shop-web`：客户商城 H5 与 Android 容器前端。
- `mall-distribution-admin`：商城运营管理后台。

旧版微服务模块已经从当前构建和交付范围隔离，不能作为当前系统能力或部署依据。

## 核心文档

- [用户操作手册](document/USER_OPERATION_MANUAL.md)
- [技术维护与接口说明](document/TECHNICAL_MAINTENANCE_API_MANUAL.md)
- [客户交付检查清单](document/private-deploy/CUSTOMER_DELIVERY_CHECKLIST.md)
- [当前发版报告](document/RELEASE_REPORT.md)
- [每日修改日志](document/DAILY_LOG.md)
- [核心业务口径](mall-distribution/document/README.md)
- [测试计划](mall-distribution/document/test-plan.md)

## 本地验证

```bash
./mvnw test
npm --prefix mall-shop-web test
npm --prefix mall-distribution-admin test
```

生产构建：

```bash
./mvnw -DskipTests package
npm --prefix mall-shop-web run build
npm --prefix mall-distribution-admin run build
```

仓库提交和合并请求还会通过`.github/workflows/quality-gate.yml`自动执行后端回归与构建、商城三端与管理后台测试构建、客户项目派生/升级工具以及私有部署安全边界检查。自动门禁不读取生产密钥，也不连接或发布服务器。

正式版本以仓库根目录的 `VERSION` 为准。发布前必须核对发版报告、线上版本、完整备份和必要回归，禁止覆盖、回退或清理客户数据。

## 版权与开源合规

长沙灵启软件开发有限公司拥有本项目自主新增及修改部分的相应权利。项目同时包含依据 Apache License 2.0 使用和修改的开源代码以及其他第三方依赖；相关代码继续遵循各自许可证。

详见：

- [版权与授权声明](COPYRIGHT.md)
- [开源及修改声明](NOTICE)
- [Apache License 2.0](LICENSE)

法律声明只保存在源码仓库和客户技术交付包中，不要求在商城消费者页面或页脚展示。
