# 1.0.165 统一验证候选（阶段回执）

日期：2026-09-24（Asia/Shanghai）。产品固定提交：`60a6e5e3fe1255bace2156f0f59089c9a341db0f`，源码树：`7e4df7d0dd8c0dd242db073f8cb21ec203a44baf`，构建号：`20260924-closure-1.0.165`。

## 已实际完成

- C/T：同一干净且已推送提交重跑四端全量自动回归、发布合同检查和生产构建，失败 0；从该提交 clean build 生成统一包。包路径 `target/releases/lingqi-mall-1.0.165-60a6e5e3.tar.gz`，字节数 `146871320`，SHA-256 `66b528b8df81ac30fee3b15d6a09647015dc8ba62ac2f0e3a41d662b23300652`；后端 JAR SHA-256 `934d345dee88ea7b9d12f8aeff5998e97c1f8d447caff5945e41d25cafbbd7ef`。41 条数据库迁移集合不变；小程序源码 296 文件，剔除源码专用文件后可上传工程 232 文件。
- 本地候选递归准入、固定小程序工程校验、正式主机与公网只读准入均通过。正式主机后端候选脚本只读预检确认原版 1.0.158、原 JAR SHA-256 `cbd249426c1dfcdad4de7dbf65f9780486b47a9807e35ae3eb27fb8252f1ae1f`、迁移 41/41，1.0.165 严格采用“只验证、不重跑 SQL”。
- 私有不可变留档：同一精确包上传到 `lifei3651/mall-swarm-release-archive` 的预发行 `candidate-1.0.165-60a6e5e3`，GitHub 页面显示附件摘要与本机相同。从平台实际下载到 `/Users/minmatemp/Downloads/lingqi-mall-1.0.165-60a6e5e3.tar.gz`；下载事件、来源主机 `release-assets.githubusercontent.com`、文件大小及本机重算 SHA-256 均核对成功。程序验签结果为 `retention-verified`；机器回执见 [artifact-retention.json](artifact-retention.json)。页面声明至少保留至 2027-09-24；这是留存承诺，不冒称平台提供自动锁期。

## 仍需独立记录

- S：正式备份、隔离恢复演练、后端及三端静态切换和部署后只读复核尚未记录为完成。
- W：微信开发版上传、体验版指向、微信审核和正式版发布均须各自留证。
- R：用户真机页面、旧售后状态、后台平台直接退款及真实渠道回调尚未验收。不能以自动测试或包哈希代替真实退款；未经用户另行发起，不操作真实退款。

本候选用于受控验证，不是 P0 全清零的正式发布候选。
