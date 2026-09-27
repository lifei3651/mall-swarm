# 1.0.168 统一候选冻结记录

日期：2026-09-27（Asia/Shanghai）。本轮只做最终跨端回归、一次本地封包和精确包验证；没有新增业务功能，没有部署、微信上传、渠道支付/退款或正式库业务写入。

后续执行更新（09:22）：用户授权本固定包的一次留档、受控部署和同版小程序分发。精确包已上传现有私有不可变 GitHub 预发行；自动下载被 Edge 拦截后由用户手动下载，Codex 核对实际下载文件的 146,906,958 字节及 SHA-256 与原包、平台附件摘要一致，见 [artifact-retention.json](artifact-retention.json)。保留至至少 2027-09-28。未改变浏览器安全设置。服务器原版后端预检通过，基线 1.0.167、正式库 41/41；临时官方 Node 22.23.2 校验通过。小程序从原包准备及预检通过。此时尚未正式迁移/切换服务器或上传微信；下表仍记录最初冻结时点。

## 结论与边界

| 层次 | 本轮状态 |
|---|---|
| C / 源码与版本 | 固定提交 `2d7371a05d431b40755f601e099e90d7defbae79`，版本 1.0.168 |
| T / 自动验证 | 后端 905、公共模块 17、后台 227、小程序 533、H5 186、脚本 45，共 1,913 项通过 |
| 本地构建与候选 | 后端 JAR、后台、公开/团队/一体 H5、小程序源码、44 条迁移同包；逐文件递归校验通过 |
| 历史隔离 MySQL 证据 | 72/72 核心服务测试及 41→44 结构副本首跑/重跑已过；见前两轮 QA。本轮未重新运行 MySQL，相关业务代码与这些证据之间无新增变更 |
| 本版独立耐久留档 | 未上传、未回读，不复用 1.0.167 回执 |
| 本版服务器 S / 微信 W | 未执行；线上仍为 1.0.167，正式迁移 41/41 |
| 真机、角色实操、渠道交易 R | 未执行，P0/P1 不因本地测试通过而自动关闭 |

## 冻结身份

- 版本：`1.0.168`；构建：`20260927-closure-1.0.168`。
- 提交：`2d7371a05d431b40755f601e099e90d7defbae79`。
- 源码树：`d4827cbcac5ea95fb3d08871902242fed30c3c8e`。
- 文件：`target/releases/lingqi-mall-1.0.168-2d7371a0.tar.gz`，146,906,958 字节。
- 整包 SHA-256：`8942efb1100da12ebd8f05adb4c89b2d2fa3874024d0317efedd0d0db891f505`。
- JAR SHA-256：`5e96f0d2f9d150d58d1efdfbb162f2d21b736d24d98625c3ad291780fc96794c`。
- RELEASE_MANIFEST SHA-256：`7285d9ce7dae4ade3636f928a1e5d48e3e1bc4c956646e3e8528fce2aae3e5fa`。
- 完整清单原样存于 [FROZEN_RELEASE_MANIFEST.json](FROZEN_RELEASE_MANIFEST.json)；包内一级文件和每条迁移摘要存于 [FROZEN_SHA256SUMS](FROZEN_SHA256SUMS)。清单文件只是证据，不等于候选包的独立留存。

本地完整路径：`/Users/minmatemp/.codex/worktrees/4868/mall-swarm-app-h5/target/releases/lingqi-mall-1.0.168-2d7371a0.tar.gz`。这是忽略目录中的本地固定包；未完成独立留档前不得清理此工作树或当作已有异地备份。后续文档回执提交不会重建或替换此包。

## 验证记录

在干净、已推送且与上游同步的提交上运行：

```sh
RELEASE_GIT_COMMIT=2d7371a05d431b40755f601e099e90d7defbae79 RELEASE_BUILD_ID=20260927-closure-1.0.168 bash scripts/run-mall-closure-regression-168.sh
node scripts/release-lingqi-168.mjs --package-only
bash scripts/release-readiness-168.sh --candidate target/releases/lingqi-mall-1.0.168-2d7371a0.tar.gz --local-only --preflight-only
node scripts/prepare-lingqi-mini-release-168.mjs --candidate target/releases/lingqi-mall-1.0.168-2d7371a0.tar.gz --validate-only
```

全部退出 0。封包器重新干净构建，未使用旧产物；后台 226、公开 H5 126、团队 H5 60、一体 H5 141 文件，版本/提交/构建号一致。小程序源码 298 文件，对不可变 Git 提交逐文件一致；可上传工程只读验证 233 文件，聚合 SHA-256 为 `43113ee76075994d26d4de9cce63514acde1a5b3ec8de716b6ea6a86e85471ac`。未替换现有上传工程，未启动微信上传。

本地预检的 `--preflight-only` 明确跳过尚未形成的耐久留存回执；**不等于正式发布准入已通过**。正式主机备份恢复、这份包内部署脚本的实际执行和平台体验版指向仍待下一阶段。

构建有既有的前端大分块体积提示，构建成功；不为此扩展本轮功能或性能重构。

日志（本机临时路径，摘要可核对，但不是独立留存）：

| 日志 | SHA-256 |
|---|---|
| /tmp/mall-168-full-regression.log | 3939cd448bbda9a3c2b74de4e8a5b56ebfef0b3feb4f24fea8f0ed736b68e1a6 |
| /tmp/mall-168-candidate-build.log | 889154291326ae7e7c025eb24539a97c118533b9d910e34a7747f17a08f519a7 |
| /tmp/mall-168-candidate-readiness.log | 15c98cebf43d8f45b1680a5804d6ebf0f450a1a971bb2352e286e667d946f78a |
| /tmp/mall-168-mini-validation.log | 512d13182481bb88be26cad1850c31f91350d1d222744e971cd2546a4f1fcb76 |

## 线上基线与迁移边界

本轮仅只读核对公网版本、服务器 VERSION/JAR 和迁移登记聚合：

- 线上 1.0.167 / `c40a0e8cea82b8c5081ba0438dc3ced0db0daf37` / `20260924-closure-1.0.167`。
- 线上 JAR SHA-256：`a1e978931d69ffae226860e32b660eaac4a7bcae5e6bfe4b0410a2e5a728d9b2`。
- 正式登记 41 条，成功 41 条。未执行 SQL 迁移或业务行修改。

固定增量：

1. `V202609261800__tenant_coupon_module_switch.sql`：优惠券开关；摘要 `c3ffa626c65d08acdd1045b464c0a3f53989df85420f843e96eba6e482f276fb`。
2. `V202609262000__tenant_balance_and_merchant_mode_switches.sql`：余额新交易/仅自营开关；摘要 `71f738563f3a8ce7c374078c8e6a97c3a038eaf9d73748aadd811eb1298819e6`。
3. `V202609262130__tenant_invitation_switch.sql`：独立邀请开关；摘要 `5e0dca4b5ec3530a1853f6e753aa12395f2f64e9fdc50d80c312abb703a903a2`。

只追加字段、旧客户默认开启。原持锁迁移器执行并校验，不能绕过迁移登记直接导入 SQL。新发布脚本要求先逐条核对已部署的 41 条前缀和三条增量摘要，正式写入前完成显式授权、精确包留档校验、完整备份与隔离首跑/重跑。失败时恢复旧 JAR/VERSION，**不删除已追加字段、不覆盖业务库、不声称数据库回退**；出现 42/43 或失败登记必须停下复核。44/44 的兼容重试继续核对开关值不被重置。脚本合同测试通过不等于上述服务器步骤实际执行。

## 下一步（有限停止线）

1. 只对这一固定包完成一次独立私有留档；不再为中间修复反复封包。
2. 获相应执行授权后，先正式机准入、备份/隔离恢复迁移演练，再受控部署服务器、独立回读；之后单独上传同版微信开发版并核对体验版指向。
3. 用户恢复验机后，按总账既有清单做角色、页面、邀请开/关以及最少受控订单的成交/售后退款/资金对账。实际第三方支付退款仍需确认测试范围。
4. 只修验收阻断项；不追加 ERP 厂商定制、监控平台或大型营销扩展。没有同版 S/W/R 证据不能写成最终完成。

关联：[核心服务 MySQL 72 项](../2026-09-27-core-mysql-closure/README.md)、[41→44 隔离迁移与财务汇总](../2026-09-26-finance-summary-and-migration-44/README.md)、[唯一收口总账](../../MALL_CLOSURE_LEDGER.md)。
