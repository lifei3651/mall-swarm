# 1.0.139 发布候选（2026-09-13）

用户明确要求发版；业务基线ba1a819d，分支codex/security-membership-payout。开始前工作区干净、pull成功。总控直接执行，不新增代理。

## 范围

- 后端137→139：评价商家/平台回复、优惠券发行/领取/抵扣及资金分摊。后台与公开/团队H5同步139；一体化H5仅构建验收，不覆盖拆分站点。
- 两项新增迁移：V202609121900__product_review_replies.sql、V202609122000__shop_coupons.sql。两张新表及16个增量字段，既有数据不改写、券默认为空，不自动发行。
- 小程序138→139：以上配套入口，以及成功提示、服务保障、搜索标签、横向滚动条、账户表单/购物车标题、密码页及客服入口修复。固定正式工程重新导出，旧目录保留；仅开发/体验版上传，不提审或正式发布。
- 不开启实名认证、微信/支付宝转账、提现资格或其他资金开关，不修改现有客户主题和规则；不发送真实短信、不提交订单/优惠券/客服消息。

## 发布前只读事实

- 正式主机VM-4-6-rockylinux，后端137，JAR SHA256 80663849f6f43f45180390f75f407f0bcf0fef2a60aa4df848be2fb9e6791c8f。
- 公开H5138/87dace7f6387266e4be12b637a28716ec69673d8；团队H5与后台137/ef47181abe9a1ad67f01f0ced92f0540caca57ab。
- 四服务active、healthUP、迁移34/34成功、磁盘可用41921MB。

## 门禁

沿用137的完整备份/哈希/受保护配置/版本/恢复门禁。139脚本仅允许已核对旧版和当前冻结提交；备份须隔离恢复，两项迁移先在恢复库执行并重复执行，检查36/36总账、新结构、历史新增字段默认值以及原业务与配置快照。停止后端后才执行正式增量迁移，再替换JAR；失败恢复旧JAR、不自动还原生产数据，兼容增量字段保留。静态三站分别保留旧站与旧哈希资源。

编译或上传成功不代表真机授权、外部客服或资金全流程通过。

## 实际测试与发布回执（2026-09-13）

- 冻结源码e6a11c3df56d4be0a7181f67457584ce6218c59f，全部候选从该提交构建；业务基线ba1a819d。封包前祖先门禁拦截机械复制的旧提交引用，修正后重新冻结构建，旧eb1150cd产物未发布。
- 后端727（common17、distribution710）、后台183、H5 153、小程序464，共1527测试通过；后台/公开/团队/一体化H5构建、同源/原生工程检查、官方34WXML及36WXSS编译通过。后端测试使用本机Java21；原先Java23沙箱内Mockito附加失败不作为通过结果。
- 后端JAR SHA256：06aae30e8580b6edecfbef080fbd1cf0a6f25376067f8ec249b84028f497d198；上传归档SHA256：bda1e09f25091b891295ad6d17eb2aaea26c2237a740fc853ce5aaeae371cb03。

| 组件 | 完整备份（发布前 → 发布后） | 保留回退目录 | 结果 |
| --- | --- | --- | --- |
| 后端 | /opt/lingqimall/backups/full/20260913_094105 → /opt/lingqimall/backups/full/20260913_094154 | /opt/lingqimall/backups/mini-backend-139.PYEElY | 139，36/36迁移成功 |
| 管理后台 | /opt/lingqimall/backups/full/20260913_094433 → /opt/lingqimall/backups/full/20260913_094445 | /opt/lingqimall/backups/admin-139.FYwuBG | 139，221文件 |
| 公开H5 | /opt/lingqimall/backups/full/20260913_094502 → /opt/lingqimall/backups/full/20260913_094514 | /opt/lingqimall/backups/public-h5-139.nnxIFo | 139，124文件 |
| 团队H5 | /opt/lingqimall/backups/full/20260913_094528 → /opt/lingqimall/backups/full/20260913_094540 | /opt/lingqimall/backups/team-h5-139.uxEwoh | 139，60文件 |

- 后端完整备份在隔离库mall_distribution_release_verify_139_20260913094117恢复验收；两迁移首次/重复执行、新表/字段、历史默认值、原业务计数/余额/配置快照全部通过。隔离库已删除，未恢复或覆盖生产数据库；原34项迁移历史与校验和保持。正式券表为空，不自动发券。
- 发布脚本完成全文件哈希、入口/资源及受保护配置核验；中断后独立SSH复核后端139、JAR上述哈希、三个站点139/e6a11c3d、四服务active及healthUP。独立公网读取三个version.json再次匹配139和冻结提交。未重复部署。
- 执行日志：/private/tmp/lingqi139-backend-deploy.log、lingqi139-admin-deploy.log、lingqi139-shop-deploy.log、lingqi139-team-deploy.log（后三项同目录）；服务器完整备份和回退目录为长期恢复依据，不依赖本机临时日志。

## 小程序上传与边界

- 固定工程/manifest：/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program 及相邻wechat-mini-program.release.json；139/e6a11c3d，AppID wxd26e0a4e41df392b，API https://lingqimall.com/api，urlCheck保持开启。旧138保留为wechat-mini-program.previous-1789263670764及相邻release.json。
- 正式工程身份与路径在开发工具窗口核实；填写1.0.139和优惠券/回复/UI修复说明，确认覆盖体验版后点击上传。10:08工具构建日志显示“上传完成，耗时：6525ms”，代码质量显示“表现良好，未发现代码质量问题”。最终成功弹窗没有捕捉，已请求用户确认；上传后279个清单文件哈希再次全部一致。
- 开发工具仍显示既有旧工具/基础库兼容错误及预加载/按需注入警告，没有把质量检查通过等同无运行时问题。未启用临时服务端口、未提审/正式发布；手机实际体验版本、头像/客服外部能力、短信授权及真实资金/优惠券业务验收仍待。
