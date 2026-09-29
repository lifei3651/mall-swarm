# 1.0.172 受控服务器部署回执

日期：2026-09-29（Asia/Shanghai）。主机 `VM-4-6-rockylinux`；前序运行版为 1.0.171。此回执只证明服务器阶段，不代替小程序平台或真机验收。

固定产品提交 `f20d75ad0e294689b1d0c81282c5e6f48e78ffaf`，构建号 `20260929-closure-1.0.172`。唯一包 `lingqi-mall-1.0.172-f20d75ad.tar.gz` 为 146917484 字节，SHA-256 `c2217b9870c7b5b44dd114b7fcc75201f3a276b006a6b09c2ab802c810cb40e8`；包内 JAR SHA-256 `3cc67936ea27f9fbd2eca5f092a0315e3644ea0b77edd337a09c2e11817c4737`。私有留档与实际下载回读见 [留档回执](artifact-retention.json)。

## 执行与独立回读

1. 包上传至远端隔离暂存 `/tmp/lingqimall-closure-172.XYJid4`，字节数、SHA-256、gzip、包内文件及留档回执均通过校验；使用包内原版脚本预检，核对前序 171、JAR 与正式迁移 44/44。
2. 后端部署前后完成完整备份；发布前备份在隔离 `m172v` 库恢复，44 条迁移首次及重跑均跳过，业务/服务标签快照不变。正式库无新增迁移或重跑 SQL，隔离库完成后清理。
3. 后端顺序切换并健康检查后，管理后台、公开商城 H5、团队 H5 依次使用同包原版脚本部署；各站精确文件树、版本清单、入口资源和受保护配置检查通过。
4. 独立回读三个公网 `version.json` 均为 `1.0.172 / f20d75ad / 20260929-closure-1.0.172`；服务器 `VERSION` 与 JAR 摘要也一致。后端、Nginx、MySQL、Redis 均 active，健康接口 UP；正式迁移 44/44，残留 `m172v` 演练库为 0。
5. 八份完整备份逐份独立通过 SHA256SUMS、gzip 和 tar 检查；四个代码回滚点存在。Nginx 配置检查通过；既有证书无 OCSP URL 的 stapling 警告未改变，未修改 TLS 配置。

| 部分 | 发布前完整备份 | 发布后完整备份 | 代码回滚点 |
| --- | --- | --- | --- |
| 后端 | `/opt/lingqimall/backups/full/20260929_172409` | `/opt/lingqimall/backups/full/20260929_172503` | `/opt/lingqimall/backups/closure-backend-172.ZIUmAz` |
| 管理后台 | `/opt/lingqimall/backups/full/20260929_172601` | `/opt/lingqimall/backups/full/20260929_172614` | `/opt/lingqimall/backups/admin-172.GiaujE` |
| 公开商城 H5 | `/opt/lingqimall/backups/full/20260929_172648` | `/opt/lingqimall/backups/full/20260929_172700` | `/opt/lingqimall/backups/public-h5-172.YnFf75` |
| 团队 H5 | `/opt/lingqimall/backups/full/20260929_172728` | `/opt/lingqimall/backups/full/20260929_172739` | `/opt/lingqimall/backups/team-h5-172.vECU12` |

部署日志保留于本机 `/private/tmp/lingqi-172-{backend,admin,shop,team}-deploy.log`，相应 SHA-256 为 `5b27f2a6c8d470f2598f4dc8a4812bf54dc2d712a704a5f7fddb7af49ea89e6e`、`4122ae1daf9d8d02e9f39ce26cebc3d6cd8786ce8892034ee6bf7ea242550a96`、`d6372d081b3cc6033ee36723f44baffb0915b531773d7841331f4a641e0eba1c`、`d15fcf3ceed3f9902167cbd0bc1c5d27647d92ed7dae58f133059701ccda935c`。正式准入日志 `/private/tmp/lingqi-172-readiness-final.log` SHA-256 `14e740102fec61d2c819627ed00293ccf15536f70cd63ab5720c2114bb7a3b2a`。

服务器只在部署进程 PATH 使用此前已核验的隔离 Node 22.23.2；没有安装全局 Node。备份留在正式机私有目录，不提交到仓库。未由本回执执行真实支付或退款。
