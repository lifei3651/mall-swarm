# 1.0.167 收口验证版服务器 S 回执

执行日期：2026-09-25（Asia/Shanghai）。用户在 1.0.167 正式主机只读准入通过后要求“下一步”，本次按已告知的边界执行正式备份、隔离演练及受控服务器部署。**这不是微信上传、体验版确认、真机验收、提审或正式发布。**

## 固定身份和发布门禁

- 唯一产品仓库本地工作区干净、与上游同步，`VERSION=1.0.167`。固定产品提交 `c40a0e8cea82b8c5081ba0438dc3ced0db0daf37`，源码树 `51acd81e7ec88cc0a987d6adddd1b4c0342deffd`，构建号 `20260924-closure-1.0.167`。
- 统一候选包 `lingqi-mall-1.0.167-c40a0e8c.tar.gz` 为 `146875450` 字节，SHA-256 `1e81bc02f11179b67ac8b5807934726118798acd0edcb0b8b956bf52e4de67ff`；正式主机暂存包大小、哈希均一致。独立私有不可变留档与实际下载回读见 [`artifact-retention.json`](artifact-retention.json)；本机和正式主机的包内原版校验器均返回 `retention-verified`。
- 发布前正式主机身份 `VM-4-6-rockylinux`；后端、管理后台、公开商城 H5、团队 H5 均为 1.0.165，旧后端 JAR SHA-256 `934d345dee88ea7b9d12f8aeff5998e97c1f8d447caff5945e41d25cafbbd7ef`，正式库迁移 `41/41`，四服务 active、健康 UP。未修改的发布总准入和包内后端 `--preflight-only` 通过，三个包内静态站 `--preflight-only` 在后端切换到 1.0.167 后逐一通过。
- 正式机没有常驻 Node。Node.js 官方 v22.23.2 Linux x64 压缩包在本机和正式主机两端依据官方 `SHASUMS256.txt` 验证，SHA-256 为 `d60acfe00a2932254bb0ad20e01b0d74397a0875595de719654b214f4b03f307`；独立临时目录的二进制只短暂通过 `/usr/local/bin/node` 链接供未修改的准入/留档脚本使用。没有系统安装、修改服务配置或修改固定包。

## 备份、隔离验证和顺序切换

包内原版脚本先部署后端，再依次部署管理后台、公开商城 H5 和团队 H5；四部分分别返回 `release-success`。后端发布前完整备份恢复到独立 `m167v_*` 数据库，核对 41 条迁移历史、`service_tags` 结构/回填及业务数量和余额快照；演练库已删除，发布后同类演练库残留数为 0。本版没有新增迁移；正式库与隔离库均只运行迁移**验证**，没有重跑第 41 条或执行新的正式 SQL。后端受控切换后，独立回读 JAR 与固定清单一致、迁移仍为 41/41，才继续静态站。

| 部分 | 发布前完整备份 | 发布后完整备份 | 回滚目录 |
|---|---|---|---|
| 后端/数据库 | `/opt/lingqimall/backups/full/20260925_181408` | `/opt/lingqimall/backups/full/20260925_181527` | `/opt/lingqimall/backups/closure-backend-167.Pymb5g` |
| 管理后台 | `/opt/lingqimall/backups/full/20260925_181614` | `/opt/lingqimall/backups/full/20260925_181627` | `/opt/lingqimall/backups/admin-167.VlDCGL` |
| 公开商城 H5 | `/opt/lingqimall/backups/full/20260925_181652` | `/opt/lingqimall/backups/full/20260925_181703` | `/opt/lingqimall/backups/public-h5-167.60ivBL` |
| 团队 H5 | `/opt/lingqimall/backups/full/20260925_181725` | `/opt/lingqimall/backups/full/20260925_181736` | `/opt/lingqimall/backups/team-h5-167.T4r749` |

八份备份的 `SHA256SUMS`、数据库 gzip 和文件归档均经独立只读命令复核通过；四个回滚目录均存在，没有执行回滚。既有证书无 OCSP responder 导致的 `ssl_stapling` 警告仍出现，Nginx 配置测试通过，不将该既有警告记作本次新故障。

## 切换后独立回读及清理

- 正式主机 `VERSION=1.0.167`，后端 JAR SHA-256 `a1e978931d69ffae226860e32b660eaac4a7bcae5e6bfe4b0410a2e5a728d9b2`，与固定包清单一致；正式库迁移 `41/41`、失败 0，隔离演练库残留 0；后端、Nginx、MySQL、Redis 均 active，health 为 UP。
- 公网管理后台、公开商城与团队 H5 的 `version.json` 独立回读均为 `1.0.167 / c40a0e8cea82b8c5081ba0438dc3ced0db0daf37 / 20260924-closure-1.0.167`。三个站的包内脚本各自校验了本机精确文件树、公开 HTML 入口与所引用资源哈希。公开/团队商城首页 API HTTP 和业务码均为 200；未登录订单接口 HTTP/JSON 均为 401。
- 从开始切换时起检查预设关键故障日志模式，命中 0。发布完成后短暂的 `/usr/local/bin/node` 链接、正式主机 Node 临时运行时与候选解包目录、当前 Mac 的 Node 下载目录均已清理；清理后再次确认线上 `VERSION=1.0.167`、四服务 active、health UP。

**结论：1.0.167 的服务器 S 已完成并独立复核。微信开发版/体验版 W、真机和真实交易 R 均未执行；P0 未清零，不提交审核或正式发布。**
