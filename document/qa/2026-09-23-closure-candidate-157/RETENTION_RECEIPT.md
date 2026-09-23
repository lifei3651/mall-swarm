# 1.0.157 候选包独立留档与回读回执

记录日期：2026-09-23（Asia/Shanghai）
执行方：Codex；用户已授权代为建立独立私有留档位置并保存候选包。
状态：**私有附件上传及实际下载回读验签完成；P0-10 留存门禁已通过。此回执不是 S/W 发布授权。**

| 字段 | 实际记录 |
|---|---|
| 版本 / 构建号 | `1.0.157` / `20260923-closure-1.0.157` |
| 产品提交 / 源码树 | `ef9da4bdfbe7bbce1d2903fe54e1edf28d119911` / `4acd081690e1fc83ad494add8ed0fd5abeffd854` |
| 本机候选文件 | `target/releases/lingqi-mall-1.0.157-ef9da4bd.tar.gz` |
| 本机字节数 | `146870042`（本轮重新读取） |
| 本机 SHA-256 | `b065fe36c1a4c9a2fc4bc71a2c1a058687cca6e46da88322fee070210a75ed9a`（本轮重新计算） |
| 独立存储 | GitHub 私有仓库 `lifei3651/mall-swarm-release-archive`；Release `candidate-1.0.157-ef9da4bd` 的同名附件 |
| 对象地址 | https://github.com/lifei3651/mall-swarm-release-archive/releases/download/candidate-1.0.157-ef9da4bd/lingqi-mall-1.0.157-ef9da4bd.tar.gz |
| 仓库保护 | 仓库设置“Enable release immutability”开启；该 Release 页面显示 `Immutable release`、`Pre-release`。这不等于管理员无法删除整个 Release。 |
| 上传结果 / 时间 | GitHub 页面显示附件已上传于 2026-09-23 10:21（Asia/Shanghai，页面精度为分钟）；Release 于 10:25 发布。机器回执的秒数 `00` 是分钟时间规范化，并非平台提供了秒级时间。 |
| 平台显示的附件 SHA-256 | `b065fe36c1a4c9a2fc4bc71a2c1a058687cca6e46da88322fee070210a75ed9a`，与本机值一致。 |
| 计划保留期限 | Release 说明要求至少保留至 2027-09-23；这是人工保留要求，不是已验证的平台自动锁期。实际若部署/上传，还须持续覆盖线上及前两版。 |
| 实际回读时间 | 用户在 Edge 完成实际下载；下载文件时间 2026-09-23 10:45:02（Asia/Shanghai），Codex 于 10:47:41 完成重算及记录。此前一次点击出现 `ERR_BLOCKED_BY_CLIENT`，已由后续真实下载取代。 |
| 回读文件 / 来源 | `/Users/minmatemp/Downloads/lingqi-mall-1.0.157-ef9da4bd.tar.gz`；文件扩展属性的来源包含 `release-assets.githubusercontent.com` 和 `github.com`，其发布资产路径标识与用户下载截图一致。没有使用本机原包或同主机审计副本冒充回读。 |
| 回读字节数 / 本地重算 SHA-256 | `146870042` / `b065fe36c1a4c9a2fc4bc71a2c1a058687cca6e46da88322fee070210a75ed9a`，与冻结包完全一致。 |
| 候选清单 SHA-256 / 程序校验 | `6db880e11557568d04a5f92a6d19af357c03c524b3c804c2f82a6e874b66f110`；`verify-release-artifact-retention.mjs` 对精确候选包和机器回执返回 `retention-verified`。 |
| 敏感文件准入 | 既有 C/T 回执记录本地敏感文件门禁通过；本轮没有重新执行完整敏感内容扫描，不把文件名检查当成完整复核。 |
| 作废 / 替代 / 删除审批 | 不适用；1.0.157 当前未作废，未授权删除。本机候选不得清理。 |

本轮没有改变浏览器扩展的“允许访问文件 URL”设置。曾请求用户授权临时开启，但相关浏览器内部设置页受安全策略阻断；随后使用系统原生文件选择器完成上传，无需开启该权限，因此也没有待关闭的新增权限。用户随后直接通过 Edge 下载，解决了首次客户端阻断；未使用被安全审核拒绝的替代浏览器回读途径。

本回执只关闭候选产物留存 P0-10；正式服务器 S、微信平台 W、真机与真实交易 R 必须分别授权、实施并记录。现有 `gates.serverDeploymentAllowed` 与 `gates.wechatDevelopmentUploadAllowed` 仍为 `false`，表示本轮尚未取得独立发布授权及正式准入回执，而非留存验签失败。
