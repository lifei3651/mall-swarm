# 1.0.154 商城收口冻结验证包

记录日期：2026-09-21（Asia/Shanghai）

## 结论与边界

`1.0.154` 已从同一干净产品提交完成冻结、全量回归、统一封包、正式服务器部署和微信开发版上传。本目录记录的是“收口验证版”证据，不是“统一发布候选”：体验版尚未指向该版本，未提审、未正式发布，微信真机和真实支付/退款/余额/发货/物流/奖金/资金链路仍未完成，因此 P0 尚未清零。

| 阶段 | 当前结论 |
|---|---|
| C（代码） | 已冻结：产品提交 `a8f86f2ed3123c21082e7404d372812e11dae790`，源码树 `8aa89734c78fa86abc5eee51428fa7d43217e717` |
| T（测试） | 已通过：后端 common 17 + distribution 761、后台 208、小程序 514、H5 178，失败 0；生产构建、迁移首跑/重跑、词汇/UI/敏感文件门禁均通过 |
| S（服务器） | 已完成：后端、管理后台、公开商城 H5、团队 H5 已部署 `1.0.154` 并独立只读复核；一体化 H5 仅构建未部署 |
| W（微信平台） | 仅开发版上传成功；`experienceVersionChanged=false`、`reviewSubmitted=false`、`formalVersionPublished=false` |
| R（真实验收） | 未完成；正式库只读查询属于 S 证据，不替代真机或真实交易 R |

## 不可变候选身份

- 版本：`1.0.154`
- 构建号：`20260921-closure-1.0.154`
- 产品提交：`a8f86f2ed3123c21082e7404d372812e11dae790`
- 源码树：`8aa89734c78fa86abc5eee51428fa7d43217e717`
- 统一候选包：`target/releases/lingqi-mall-1.0.154-a8f86f2e.tar.gz`
- 统一候选包大小：`146810033` 字节
- 统一候选包 SHA-256：`92dd37887821b3dc44db9ee30c5aff8f15498d94e90af16a9dcb9af333f7adb0`
- 封包结果 JSON SHA-256：`13fbdb6fe82518c14f3b83502541b8b886b4cd3d525b8f687b2ebf71d2a9860c`
- `RELEASE_MANIFEST.json` SHA-256：`eb756829b251874f1301a2c4a09a7100d5e12160599030501c6e0048f4f3105d`
- 包内 `SHA256SUMS` SHA-256：`3524697e57f761d5d776a793a55a069757cf4c11642ad1da8310ba9e8b7a5c36`
- 40 条迁移清单 `MIGRATIONS.sha256` SHA-256：`e6b86392764476758a95d98f74ddbf8820e6ecb039eb3325f94f048bd4028c36`
- 完整清单：`ARTIFACTS.sha256`、`MIGRATIONS.sha256`、`EVIDENCE.sha256`

## 全量回归与构建

- 后端 Surefire：`mall-common` 17 项、`mall-distribution` 761 项，失败 0、错误 0、跳过 0。
- 管理后台 Vitest：208 项通过；生产构建通过。
- 微信小程序：514 项通过；工程、词汇、共用界面合同和同源生成物检查通过。
- 商城 H5：178 项通过；公开、团队、一体化三种生产构建和边界检查通过。
- 数据库迁移：40 条首跑/重跑演练通过，最后一条为 `V202609202100__system_fund_accounts.sql`，本轮没有新增迁移。
- 订单卡片隔离渲染：320px 与 390px 各覆盖待支付、待发货、待收货、已完成、已取消五种状态；每卡 220px，五行顺序正确，操作按钮 28px（等比 56rpx）、同组单行右对齐、主按钮最右且无横向溢出。
- 发布准入：统一候选本地准入和正式环境只读预检均通过。

订单卡片证据：

- `order-cards/orders-320.png`：`2cd3a92ab3f05d6c500239a341e464742e176fc65b7d0cd1db045103fcfeb20a`
- `order-cards/orders-390.png`：`dc36b3a8e47df225b48f5c9b83d1d2869a6c6d7bd8efeb138999423729b532e0`
- `order-cards/result.json`：`8bd340df8558e81bd85d68fb661df00024e3adea19609a6f4f44a0f78668aeef`

## 包内产物

| 产物 | 文件数 | SHA-256 | 状态 |
|---|---:|---|---|
| 后端 JAR | — | `786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96` | 已部署 |
| 管理后台 | 225 | `44e98235a1f789dcbded83073c0c56e7d9ee7c6ccbd3c1c4c603b2edc2e65683` | 已部署 |
| 公开商城 H5 | 125 | `81343e49612e2f69d62b3e5d0ef230e70287b7f0e92d4ffa0b008b393e9411c5` | 已部署 |
| 团队 H5 | 60 | `6bfe74f0d4127b578b5d512229f455d944bad8477d765561e74101431a5ce223` | 已部署 |
| 一体化 H5 | 139 | `6011da02eb9bf5359d6bfbc7c5e02a1f0702ea7147bce5eddbdacc7fb0491329` | 仅构建 |
| 小程序源码包 | 290 | `58c5496ad61d1e58854ff77e67eaf9d9c549d26847fe62ba593d26dd18d0b955` | 已导出 |
| 小程序源码清单 | 290 | `e6399487f3daac2e7e70c32c96a86be3b2239cc04a4d9f5ed7d04e6b7fbabc7f` | 已核对 |

小程序源码清单的 290 文件聚合 SHA-256 为 `0c1f67e8a699467b5c121447dbad88b7c6db69268cd1349d5a99c23898b76758`；微信实际上传工程经排除测试和脚本后为 228 文件，聚合 SHA-256 为 `440db5a492ee8be421ba0b49f35416c5eeff677a9b7c356f387d79371068a862`。两者用途不同，不得混用。

## 正式服务器发布 S

- 后端从 `1.0.153` 受控发布到 `1.0.154`。发布前/后完整备份：`/opt/lingqimall/backups/full/20260921_153454`、`/opt/lingqimall/backups/full/20260921_153535`；回滚目录：`/opt/lingqimall/backups/closure-backend-154.u7fdg1`。
- 管理后台逐文件发布 225 项。发布前/后完整备份：`/opt/lingqimall/backups/full/20260921_153629`、`/opt/lingqimall/backups/full/20260921_153641`；回滚目录：`/opt/lingqimall/backups/admin-154.b96AcZ`。
- 公开商城 H5 逐文件发布 125 项。发布前/后完整备份：`/opt/lingqimall/backups/full/20260921_153712`、`/opt/lingqimall/backups/full/20260921_153723`；回滚目录：`/opt/lingqimall/backups/public-h5-154.z2Kj31`。
- 团队 H5 逐文件发布 60 项。发布前/后完整备份：`/opt/lingqimall/backups/full/20260921_153758`、`/opt/lingqimall/backups/full/20260921_153810`；回滚目录：`/opt/lingqimall/backups/team-h5-154.omUUuA`。
- 一体化 H5 只作为验证产物构建，未部署。
- 发布后独立只读复核：主机名正确；正式版本 `1.0.154`、JAR 哈希与候选一致；四服务 active、Redis PONG、health UP；迁移 40/40；管理后台、公开商城、团队 H5 的 `version.json` 均为同一版本/提交/构建号；受保护订单接口 HTTP 401 且 JSON `code=401`；关键错误日志命中 0。
- 正式业务只读复核：系统归集账户 2、缺失归集订单 0；发货同步、物流消息、奖金、ERP 积压均为 0；退款/提现超时均为 0；商户账本差异 0。
- `/actuator/prometheus` 返回 404。业务监控代码已随服务部署，但 Prometheus 采集、规则加载、Alertmanager 路由及告警触发/恢复均未验收，不能写成已投入值守。
- 本次远程候选临时目录已在核对主机名和精确路径后清理；全部备份与回滚目录保留。

## 微信开发版上传 W

- 固定上传工程：228 文件，逐文件清单见 `wechat-upload/2026-09-21_07-49-14-714/upload-project-manifest.json`。
- 官方 CLI 回执：`wechat-upload/2026-09-21_07-49-14-714/receipt.json`。
- 官方结果：`success=true`、退出码 0、输出 `✔ upload`，包大小 `1542250` 字节。
- 临时服务端口 `32227` 已关闭。
- 本轮只执行开发版上传；`experienceVersionChanged=false`、`reviewSubmitted=false`、`formalVersionPublished=false`。

## 正式反馈账号关系只读复核

反馈手机号对应唯一会员且账号启用，手机号就是商城登录账号；直属邀请关系为 `NONE`，`team_opt_in=0`，启用推广身份、有效团队关系、直属邀请会员、直属团队成员均为 0。本轮没有补绑邀请关系、修改身份或绕过邀请资格。该结论解释了“无邀请人”的当前数据事实，但不替代 P0-09 的分享注册真机验收。

## 尚未完成与禁止宣称

- 未把微信体验版指向 `1.0.154`，未在手机确认实际版本/提交。
- 未提审，未正式发布微信小程序。
- 未完成设置中心逐角色、订单卡片、账号已绑/未绑/异常等微信真机验收。
- 未用同一候选新建并闭环真实微信支付、余额支付、退款、售后、发货、物流、奖金和资金归集订单。
- 未完成支付宝、短信、实名、自动打款、特定 ERP 供应商等外部能力验收。
- 未完成 Prometheus/Alertmanager 的正式采集、规则、路由、触发和恢复验收。

因此，本包只能称为“`1.0.154` 收口验证版 C/T/S + 微信开发版 W”，不能称为 P0 已清零、统一发布候选或正式上线完成。
