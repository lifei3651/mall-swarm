# 1.0.175 受控服务器部署回执

2026-10-01（Asia/Shanghai）。用户手动上传原固定附件、一次私有不可变留档元数据核验及正式准入通过后，执行包内原版后端、后台、公开商城H5、团队H5脚本；四阶段均退出0。没有重新构建候选、改变业务配置、处理真实资金或覆盖历史固定包。

## 固定身份与部署结果

- 产品提交：`012770fd343e666a0eef50600806cd07905ac1dd`；源码树：`fc06230312541e2d4485a06c5b0df6c4a4be7716`；版本/构建：`1.0.175 / 20261001-closure-1.0.175`。
- 原包：`lingqi-mall-1.0.175-012770fd.tar.gz`，146967573字节，SHA-256 `43cf97ace7222440a41a87de4fe8367338a808b2b33a133ba78b376ec59755ca`。正式机暂存 `/tmp/lingqimall-closure-175.0OtpXp`，本机、留档服务摘要、服务器原包及解包清单一致。
- 唯一主机：`VM-4-6-rockylinux / lingqimall.com`；原后端及三站174基线已核对，发布后后端175 JAR SHA-256 `5b94000f22d2f5e059e654b36aeecd5436372ee394062bb497d18dbf2046df0d`。
- 正式与隔离恢复均为45→45 `verify-45`，隔离首跑和重跑通过，**未apply任何迁移**。正式登记45/45、失败0，各脚本原SHA-256 45/45相等，临时 `m175v` 数据库残留0。
- 停服发布窗口逐表、全部列HEX/SQL NULL逐行守恒通过；历史首笔订单及时间、商品服务标签、租户配置及规则版本保护通过。没有按当前订单重新推导历史首笔、要求历史新字段为NULL，或写入邀请/资格/奖金/资金配置。
- 后端健康UP；`lingqimall-distribution.service`、nginx、mysqld、redis均active；后端受保护接口401合同、关键日志及私有配置保护通过。仅使用既有已验签临时Node22.23.2，不安装或替换全局运行时。
- 后台226、公开商城126、团队60个文件，独立逐文件摘要全部通过，额外/缺失均0，身份与原固定候选一致。可选一体H5保留在候选中，未新增线上站点。

## 八份完整备份与四个代码回滚点

| 阶段 | 部署前完整备份 | 部署后完整备份 | 原代码回滚点 |
| --- | --- | --- | --- |
| 后端 | `/opt/lingqimall/backups/full/20261001_092908` | `/opt/lingqimall/backups/full/20261001_093014` | `/opt/lingqimall/backups/closure-backend-175.XibqKN` |
| 后台 | `/opt/lingqimall/backups/full/20261001_093134` | `/opt/lingqimall/backups/full/20261001_093147` | `/opt/lingqimall/backups/admin-175.DndBla` |
| 公开商城H5 | `/opt/lingqimall/backups/full/20261001_093236` | `/opt/lingqimall/backups/full/20261001_093247` | `/opt/lingqimall/backups/public-h5-175.FdvxcI` |
| 团队H5 | `/opt/lingqimall/backups/full/20261001_093330` | `/opt/lingqimall/backups/full/20261001_093341` | `/opt/lingqimall/backups/team-h5-175.uDmIVw` |

独立检查八份备份的SHA-256、gzip与tar完整性通过；后端发布前备份VERSION为174，其余七份为175。四个回滚点保留原174代码，静态身份为`72f4db846f0307c65bb2ea9cb75093e1968621e4 / 20260930-closure-1.0.174`，旧JAR SHA-256 `d4f0a0fb6e0aa4e4902e01f97c236155bc1e52bd33885fe319738b375ffce1c6`。没有恢复备份覆盖正式业务库；私有备份及业务行不纳入Git。

## 独立发布后核验

2026-10-01 09:36:53（Asia/Shanghai）独立只读后置检查通过：原部署日志、版本/JAR、45条登记及摘要、四服务健康、三站完整静态树、八份备份、四个回滚点、演练库清理均与上表及固定候选相等。10份私有基线均存在、root所有且仅所有者可访问；历史首笔1行的订单和时间精确相等、历史服务标签8行字节相等；租户43列/1行与规则版本10列/1行全部列摘要仍等于发布前基线。重启后的动态业务表不要求永远保持总摘要不变；本次守恒依据为原脚本停服窗口检查，不妨碍正常后台业务。

公网独立读取后台、公开商城、团队站的version/index及HTML实际入口JS/CSS，均为175/012770fd/本版构建号，文件摘要与原候选逐项相等（后台14个入口资源、公开商城3个、团队3个）。没有以网页标题或开发工具预检替代实际服务器分发。

本机实际日志（仅诊断，不上传生产数据）：

- `/private/tmp/mall-175-final-readiness.log`：正式准入退出0。
- `/private/tmp/mall-175-server-backend.log`、`mall-175-server-admin.log`、`mall-175-server-shop.log`、`mall-175-server-team.log`：四阶段直接执行回执。
- `/private/tmp/mall-175-server-postflight.log`：3026字节，SHA-256 `93a0ef333eb5701355d84d599faf7f5c97e9fc1749f20e4b471e95fc54055afe`，独立核验passed。
- `/private/tmp/mall-175-public-postflight.log`：三个站点的独立公网摘要核验passed。

## 交接边界

服务器S已完成，同版[微信开发版W](MINI_UPLOAD_20261001.md)已上传；二者不代替手机R。未主动改变体验版、提交审核或发布小程序正式版。既往真实支付退款记录继续有效，174原JAR的61组HTTP证据按原日期保留、不宣称175重跑；下一步仅同版受影响真机验收，P1-24尚未因发布而关闭。
