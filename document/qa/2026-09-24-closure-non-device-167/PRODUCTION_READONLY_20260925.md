# 1.0.167 正式主机只读准入回执（2026-09-25）

本次依据用户对“临时准备经校验的 Node 20+，只执行 1.0.167 只读准入、不部署”的确认执行。对象仅为固定提交 `c40a0e8cea82b8c5081ba0438dc3ced0db0daf37`、构建号 `20260924-closure-1.0.167`、统一包 `lingqi-mall-1.0.167-c40a0e8c.tar.gz`，大小 `146875450` 字节、SHA-256 `1e81bc02f11179b67ac8b5807934726118798acd0edcb0b8b956bf52e4de67ff`。

## 直接结果

- 开始前本机仓库干净、与上游同步，`VERSION=1.0.167`；公网公开商城、团队 H5 和管理后台三个 `version.json` 均为 `1.0.165 / 60a6e5e3fe1255bace2156f0f59089c9a341db0f / 20260924-closure-1.0.165`。正式主机身份为 `VM-4-6-rockylinux`，原无 `node` 命令，Nginx/MySQL/Redis/后端四服务 active，后端健康为 UP。
- 正式主机直连 Node.js 官方 v22.23.2 下载超时后，已停止该传输并确认其临时目录清理。改由当前 Mac 从 Node.js 官方发布目录下载 Linux x64 压缩包和 `SHASUMS256.txt`；本机与正式主机分别用官方清单核验压缩包 SHA-256 `d60acfe00a2932254bb0ad20e01b0d74397a0875595de719654b214f4b03f307`，正式主机运行版本确认为 `v22.23.2`。二进制只位于本次专用临时目录；为了让未修改的准入脚本发现它，`/usr/local/bin/node` 短暂链接到该文件，没有安装软件包、修改系统服务或应用配置。
- 从已推送仓库运行**现有未修改的** `scripts/release-readiness-167.sh`，传入精确候选包及已提交的 `artifact-retention.json`，返回四项 `release-readiness-ok`：唯一仓库/远程同步/主机隔离、统一包及四个静态内包/小程序/迁移清单/Nginx 模板、P0-10 独立留档回读、正式主机身份/四服务/健康/公网基线/Nginx 模板。不是跳过 Node 门禁的包外替代脚本。
- 将同一候选包及留存回执暂存于正式主机独立 `/tmp/lingqimall-closure-167.*` 目录；正式主机重算候选大小和 SHA-256 后才解包。包内原版 `verify-artifact-retention.mjs` 连同包内 manifest 返回 `retention-verified`。**包内原版** `release-backend.sh --preflight-only` 返回 `release-preflight=passed previous=1.0.165 target=1.0.167 migrations=41:41 migration-mode=verify-only`。
- 预检后独立只读复核：正式主机仍为 `VERSION=1.0.165`，后端 JAR SHA-256 `934d345dee88ea7b9d12f8aeff5998e97c1f8d447caff5945e41d25cafbbd7ef`，正式库迁移 `41:41`，四服务 active、健康 UP；公网三站仍为 1.0.165。随后移除本轮 `/usr/local/bin/node` 链接、正式主机的运行时及候选临时目录、当前 Mac 的临时下载目录；正式主机回读确认链接与目录均不存在，四服务仍 active、健康 UP。

## 不得外推

- 本次只读准入没有进入 `--authorize-release` 分支；没有创建发布备份、隔离恢复库、执行迁移 SQL、切换 JAR 或静态站、重启服务、上传微信、操作退款或做真机交易。正式服务器与微信的 1.0.167 S/W/R 均未完成，P0 仍未清零。
- 包内静态站发布脚本要求正式后端先成为 1.0.167，因此当前在线后端仍为 1.0.165 时不能把静态站脚本的后置预检记作通过。本次已完成其包内身份和模板的本地准入，但没有绕过它的阶段顺序。
- Node 门禁提前检查及本次临时运行时的只读预检已验证；正式主机**没有常驻 Node**。若今后获准进入服务器 S，必须重新提供经过官方摘要校验的临时运行时、重新运行正式准入及包内预检；本次结果不能代替发布当时的在线状态或备份/隔离演练。
