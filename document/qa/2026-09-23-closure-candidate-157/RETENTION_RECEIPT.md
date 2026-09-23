# 1.0.157 候选包独立留档回执（部分完成）

记录日期：2026-09-23（Asia/Shanghai）
执行方：Codex；用户已授权代为建立独立私有留档位置并保存候选包。
状态：**附件上传完成；实际下载回读未完成；P0-10 未关闭；禁止 S/W。**

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
| 上传结果 / 时间 | GitHub 页面显示附件已上传，约 2026-09-23 10:21（Asia/Shanghai）；Release 于约 10:25 发布。 |
| 平台显示的附件 SHA-256 | `b065fe36c1a4c9a2fc4bc71a2c1a058687cca6e46da88322fee070210a75ed9a`，与本机值一致；**此项不能代替实际下载回读。** |
| 计划保留期限 | Release 说明要求至少保留至 2027-09-23；这是人工保留要求，不是已验证的平台自动锁期。实际若部署/上传，还须持续覆盖线上及前两版。 |
| 实际回读时间 / 字节数 / 本地重算 SHA-256 | **未完成 / 未取得 / 未取得**。附件点击下载时 Edge 报 `ERR_BLOCKED_BY_CLIENT`。不得根据平台显示哈希预填回读结果。 |
| 敏感文件准入 | 既有 C/T 回执记录本地敏感文件门禁通过；本轮没有重新执行完整敏感内容扫描，不把文件名检查当成完整复核。 |
| 作废 / 替代 / 删除审批 | 不适用；1.0.157 当前未作废，未授权删除。本机候选不得清理。 |

本轮没有改变浏览器扩展的“允许访问文件 URL”设置。曾请求用户授权临时开启，但相关浏览器内部设置页受安全策略阻断；随后使用系统原生文件选择器完成上传，无需开启该权限，因此也没有待关闭的新增权限。替代浏览器回读途径同样受安全审核拒绝，没有绕过。

后续仅在获准的下载路径可用时，从上述私有对象**实际下载**到独立临时路径，记录下载完成时间、文件字节数、本地 SHA-256，并与冻结值逐字节对照。未完成前，本回执不得作为放行 S/W 的证据。
