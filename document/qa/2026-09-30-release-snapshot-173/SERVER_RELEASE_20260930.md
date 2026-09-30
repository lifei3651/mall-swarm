# 1.0.173 受控服务器部署回执

日期：2026-09-30（Asia/Shanghai）。主机 `VM-4-6-rockylinux`，前序运行版 `1.0.172`。仅证明服务器阶段，不代表微信体验版或真机验收。

固定产品提交 `df9937c2fdb484e15c834d0d440de984da7adb1a`、源码树 `a5042bbefc568d3d93f6a42e6e1d9b3c67cf4630`、构建号 `20260930-closure-1.0.173`。唯一包 `lingqi-mall-1.0.173-df9937c2.tar.gz` 为 146921630 字节，SHA-256 `5fb33cae4d7ea6b6ecb6bbd8268c1a79f2614b06a4720fdc1c8aea64f1152fa0`；包内 JAR SHA-256 `d3782cb36f1c8fd62822f7c7c79c7611ae53bacc150a2d13d5d9a8aac58d1b6e`。私有不可变留档与实际下载回读见 [留档回执](artifact-retention.json)。

1. 同一候选包上传至远端隔离目录 `/tmp/lingqimall-closure-173.CRvPcr`；远端字节数、摘要、压缩包、包内清单及留档回执复验通过。原包后端预检核对 172 与正式迁移 44/44。静态站点要求后端先切 173，前序运行在 172 时拒绝静态预检，是预期顺序保护。
2. 后端发布前后各完成完整备份；发布前备份进入隔离 `m173v` 库，44 条迁移首跑与重跑均跳过，业务快照不变。正式库没有新增或重跑迁移。后端健康检查通过后，管理后台、公开商城 H5、团队 H5 依次用固定包原版脚本预检和切换；文件树、入口资源、Nginx 配置及受保护配置校验通过。
3. 独立公网回读 `https://lingqimall.com/version.json`、`https://lingqimall.com/admin/version.json`、`https://www.lingqimall.com/version.json` 均为 `1.0.173 / df9937c2 / 20260930-closure-1.0.173`。正式主机 `VERSION` 为 173，应用 JAR SHA-256 与固定包一致；后端、Nginx、MySQL、Redis 均 active，健康接口 `UP`。正式迁移只读回读仍 `44:44`，残留 `m173v` 演练库为 0。
4. 八份完整备份均经独立 `SHA256SUMS`、数据库 `gzip -t` 和配置归档 `tar -tzf` 检查通过；四个代码回滚点存在。Nginx 检查中出现原有证书无 OCSP responder 的 `ssl_stapling` 警告，配置语法与服务正常，未修改 TLS 配置。没有在本次服务器发布中执行真实支付或退款。

| 部分 | 发布前完整备份 | 发布后完整备份 | 代码回滚点 |
| --- | --- | --- | --- |
| 后端 | `/opt/lingqimall/backups/full/20260930_111516` | `/opt/lingqimall/backups/full/20260930_111608` | `/opt/lingqimall/backups/closure-backend-173.vCWtwa` |
| 管理后台 | `/opt/lingqimall/backups/full/20260930_111701` | `/opt/lingqimall/backups/full/20260930_111714` | `/opt/lingqimall/backups/admin-173.949JsO` |
| 公开商城 H5 | `/opt/lingqimall/backups/full/20260930_111742` | `/opt/lingqimall/backups/full/20260930_111753` | `/opt/lingqimall/backups/public-h5-173.ABQPD9` |
| 团队 H5 | `/opt/lingqimall/backups/full/20260930_111920` | `/opt/lingqimall/backups/full/20260930_111931` | `/opt/lingqimall/backups/team-h5-173.f3zzNx` |

服务器仅对部署进程使用此前已验证的隔离 Node 22.23.2，未全局安装。备份保留在服务器私有目录，未入仓库。
