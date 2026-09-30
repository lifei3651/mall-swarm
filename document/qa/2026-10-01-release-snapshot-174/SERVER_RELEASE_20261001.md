# 1.0.174 受控服务器部署回执

日期：2026-10-01（Asia/Shanghai）。主机`VM-4-6-rockylinux`，前序运行版1.0.173。此记录证明服务器阶段，不代表微信体验版或真机验收。

固定产品`72f4db846f0307c65bb2ea9cb75093e1968621e4`、源码树`f5996555c065fa1561465fcea30fcccae87a2eea`、构建号`20260930-closure-1.0.174`。唯一146966963字节包SHA-256 `adedd0c629a813ac5f8749ede69763af67b5c778f9489db304403a75b169687e`；包内与正式JAR均`d4f0a0fb6e0aa4e4902e01f97c236155bc1e52bd33885fe319738b375ffce1c6`。一次私有不可变上传及服务摘要/准确大小回读见[留档回执](artifact-retention.json)。

1. 原包在服务器独立目录`/tmp/lingqimall-closure-174.OHsV4j`复验准确摘要、全部包内文件和留档回执；原版后端预检确认前序173、44/44及原JAR。正式准入要求已提交/推送回执，不绕过留档或修改冻结脚本。
2. 发布前完整备份实际恢复到专属`m174v_`隔离库，原版第45条SQL首跑及重跑通过；旧44条登记不变，旧业务表各旧列逐行HEX投影摘要、资金聚合、模块开关、历史服务标签不变；新字段保持历史NULL，首笔支付标记精确新增1条且重跑创建时间不变。隔离库清理后，暂停后端，正式库仅执行第45条增量，同样核对旧数据/资金/配置后才切换JAR和VERSION；没有恢复覆盖正式库业务数据。
3. 后端健康通过后，原版脚本依次预检、备份和更新后台/公开H5/团队H5，各完整文件树与入口资源校验通过，其他站点/配置均保持。微信开发版在后端45/45成功后并行上传，未等待或修改用户账户业务配置。
4. 独立公网回读`https://lingqimall.com/version.json`、`https://lingqimall.com/admin/version.json`、`https://www.lingqimall.com/version.json`均为`1.0.174 / 72f4db84 / 20260930-closure-1.0.174`，edition/application匹配。独立逐文件复验后台226、公开126、团队60文件，与原候选完全一致；可选一体H5只保留候选，不新增线上站点。
5. 独立服务器回读VERSION/JAR同版，后端/Nginx/MySQL/Redis均active、健康UP；全部45条登记逐条与固定SQL摘要一致，45/45成功，`m174v`残留0。新历史字段0:0:0、首笔标记1条与历史最早支付精确一致。再次对dms_tenant及规则版本表旧列与切换前摘要比对，**现有邀请/模块开关、推广资格和奖金政策未变**。
6. 八份完整备份独立SHA256SUMS、数据库gzip及配置tar检查通过；四个旧版代码回滚点独立验证，旧JAR/三站回滚版本均173。首次独立检查把静态回滚路径少写了一层，纠正为`回滚点/站点/version.json`后原样只读全部复核通过；不是部署失败或重新发版。

| 部分 | 发布前完整备份 | 发布后完整备份 | 代码回滚点 |
| --- | --- | --- | --- |
| 后端 | `/opt/lingqimall/backups/full/20261001_004238` | `/opt/lingqimall/backups/full/20261001_004346` | `/opt/lingqimall/backups/closure-backend-174.1DauFd` |
| 管理后台 | `/opt/lingqimall/backups/full/20261001_004518` | `/opt/lingqimall/backups/full/20261001_004531` | `/opt/lingqimall/backups/admin-174.D5ntx6` |
| 公开H5 | `/opt/lingqimall/backups/full/20261001_004649` | `/opt/lingqimall/backups/full/20261001_004700` | `/opt/lingqimall/backups/public-h5-174.fBM9EJ` |
| 团队H5 | `/opt/lingqimall/backups/full/20261001_004824` | `/opt/lingqimall/backups/full/20261001_004835` | `/opt/lingqimall/backups/team-h5-174.9lj6AZ` |

Nginx存在原有证书无OCSP responder的ssl_stapling警告，语法和服务正常，未改TLS。只对发布进程使用此前已验签Node22.23.2，未全局安装或修改链接。私有备份留服务器，不下载、不入Git；没有退款、支付、清理业务数据、切换奖金政策或提交微信审核。

本机临时核验日志：`/private/tmp/mall-174-server-backend.log`、`mall-174-server-admin.log`、`mall-174-server-shop.log`、`mall-174-server-team.log`、`mall-174-server-postflight.log`及`mall-174-public-postflight.log`。日志只供当前主机查证，敏感备份和客户资料不进入源码仓库。
