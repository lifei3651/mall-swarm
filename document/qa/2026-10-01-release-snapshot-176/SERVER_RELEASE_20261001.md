# 1.0.176 受控服务器部署回执

2026-10-01（Asia/Shanghai）。用户手动上传原固定附件，一次私有不可变留档元数据核验及正式准入退出0后，执行包内原版后端、后台、公开商城H5、团队H5脚本；四阶段均退出0。没有重新构建候选、改变业务配置、处理真实资金或覆盖历史固定包。

## 固定身份与直接部署结果

- 产品提交：`4d6254a6ac243d1bf19ecfd4e749eb9fadfc17f1`；源码树：`2d005f97c13669a99d98bdb312ad01558b103455`；版本/构建：`1.0.176 / 20261001-closure-1.0.176`。
- 原包：`lingqi-mall-1.0.176-4d6254a6.tar.gz`，146969996字节，SHA-256 `047e99ec7e791884381f2ad66935dbf6500ceca703aaea40857556403c283ab6`。正式机暂存 `/tmp/lingqimall-closure-176.u61PN8`，本机、留档服务摘要、服务器原包及解包清单一致。
- 唯一主机：`VM-4-6-rockylinux / lingqimall.com`；原后端及三站175基线已核对，发布后后端176 JAR SHA-256 `6c44e3513bf54c7263cdea09471f3392251c4f5d9765d354c240f85ef841079b`。
- 正式与隔离恢复均为45→45 `verify-45`，隔离首跑和重跑通过，**未apply任何迁移**。原脚本停服窗口逐表、全部列HEX/SQL NULL逐行守恒通过，历史首笔订单和时间、商品服务标签、租户配置及规则版本保护通过；没有重新推导历史首笔或改邀请/资格/奖金/资金配置。
- 四阶段均核对四服务active、后端health UP、私有配置及非目标代码保护。后台226、公开商城126、团队60个文件精确树及各自公网version/index/入口资源检查通过；可选一体H5仅留在候选，未新增线上站点。使用既有临时Node22.23.2，不安装或替换全局运行时。

## 八份完整备份与四个代码回滚点

| 阶段 | 部署前完整备份 | 部署后完整备份 | 原代码回滚点 |
| --- | --- | --- | --- |
| 后端 | `/opt/lingqimall/backups/full/20261001_112615` | `/opt/lingqimall/backups/full/20261001_112720` | `/opt/lingqimall/backups/closure-backend-176.E2LqR2` |
| 后台 | `/opt/lingqimall/backups/full/20261001_113459` | `/opt/lingqimall/backups/full/20261001_113511` | `/opt/lingqimall/backups/admin-176.nZ3Ly1` |
| 公开商城H5 | `/opt/lingqimall/backups/full/20261001_113556` | `/opt/lingqimall/backups/full/20261001_113607` | `/opt/lingqimall/backups/public-h5-176.JHYzlq` |
| 团队H5 | `/opt/lingqimall/backups/full/20261001_113642` | `/opt/lingqimall/backups/full/20261001_113654` | `/opt/lingqimall/backups/team-h5-176.WUwAp6` |

原部署脚本分别检查八份备份的SHA-256、gzip及tar完整性。后端发布前备份VERSION为175，其余七份为176；四个回滚点保留原175代码，不恢复备份覆盖正式业务库。私有配置、业务行和完整备份不纳入Git。

## 独立发布后核验

2026-10-01 11:39:27（Asia/Shanghai）独立只读后置检查通过：四阶段原日志、版本/JAR、45条成功登记及原摘要、四服务active、health UP、Redis PONG、三站完整文件树、八份备份及四个回滚点全部匹配固定候选和上表。演练 `m176v` 数据库残留0，原发布journal关键错误0。

10份私有基线均存在、root所有且仅所有者可访问。历史首笔1行的订单和时间精确相等，历史服务标签8行字节相等；租户43列/1行及规则版本10列/1行的全列摘要等于发布前基线。26个私有配置文件的集合与摘要保持不变。重启后的动态业务表不要求永远维持总摘要不变；全业务守恒依据为原脚本停服窗口检查，正常业务不受阻。

独立公网读取后台、公开商城、团队站的version/index及HTML实际入口JS/CSS，均为176/4d6254a6/本版构建号，摘要与原候选逐项相等（后台14个入口资源、公开商城3个、团队3个）。两个核验脚本均退出0，没有运行部署脚本、写业务数据或修改配置。

独立日志：`/private/tmp/mall-176-server-postflight.log`，3284字节、SHA-256 `09366b29fc715226e35276d15d18fe5e78919742cd827f35dc2b1fae1608633e`；`/private/tmp/mall-176-public-postflight.log`，567字节、SHA-256 `e0a045601f63cebd0f47e3af16b3642b245c889c71b6226c8a3e2fb35ec81da6`。日志保存时间11:45:53不替代上述服务器读取时间。

本机直接日志：`/private/tmp/mall-176-final-readiness.log`（正式准入退出0），`/private/tmp/mall-176-server-backend.log`、`mall-176-server-admin.log`、`mall-176-server-shop.log`、`mall-176-server-team.log`（四阶段退出0）。

## 交接边界

同版[微信开发版](MINI_UPLOAD_20261001.md)已上传。此次仅分发单笔订单详情重复状态显示修复，顶部部分退款说明、售后进度及逐件退款标记保留，未改支付/退款/履约规则。没有主动改变体验版、提交审核或发布微信正式版。手机复测及P1-24真实短信/新旧单余额付款不因分发自动完成；既往真实支付退款及174原JAR的61组HTTP证据保留，不重复实跑。
