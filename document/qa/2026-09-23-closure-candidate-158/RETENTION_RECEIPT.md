# 1.0.158 候选包独立留档与实际回读回执

记录日期：2026-09-23（Asia/Shanghai）
执行方：Codex；用户明确同意上传 1.0.158 私有留档并回读核验。
状态：**私有附件、不可变预发行与实际下载回读验签完成；只关闭本候选的 P0-10。本回执不是 S/W 发布授权。**

| 字段 | 实际记录 |
|---|---|
| 版本 / 构建号 | `1.0.158` / `20260923-closure-1.0.158` |
| 产品提交 / 源码树 | `fcadf4099861a0a5359b12761cbf232c160635a7` / `cf898394b8465b8e2b4602c085ecb43c13b326b1` |
| 本机候选文件 | `target/releases/lingqi-mall-1.0.158-fcadf409.tar.gz` |
| 本机字节数 / SHA-256 | `146869427` / `6426676a6fc0814cedee9c412110e95e3e12a533747201d2d64c8b42cdc8bd3e` |
| 候选包内清单 SHA-256 | `39b650b97ffa1fd24876df776d85bb035437d872e9fb7e7f5ad5f7d5ba53db72` |
| 独立存储 | GitHub 私有仓库 `lifei3651/mall-swarm-release-archive`；预发行 `candidate-1.0.158-fcadf409` 的同名附件 |
| 对象地址 | https://github.com/lifei3651/mall-swarm-release-archive/releases/download/candidate-1.0.158-fcadf409/lingqi-mall-1.0.158-fcadf409.tar.gz |
| 仓库保护 | 页面显示 `Immutable release` 和 `Pre-release`；标签及附件发布后不可修改，但不等于管理员无法删除整个 Release。 |
| 上传结果 / 时间 | GitHub 页面显示附件上传于 2026-09-23 12:01（Asia/Shanghai，分钟精度）；预发行于 12:05 发布。机器回执将上传秒数规范化为 `00`。 |
| 平台显示的附件 SHA-256 | `6426676a6fc0814cedee9c412110e95e3e12a533747201d2d64c8b42cdc8bd3e`，与本机一致。 |
| 实际回读文件 / 来源 | `/Users/minmatemp/Downloads/lingqi-mall-1.0.158-fcadf409.tar.gz`；Edge 下载列表显示该文件，扩展属性的来源主机为 `release-assets.githubusercontent.com`，不记录一次性签名下载 URL。 |
| 实际下载与复核时间 | 文件修改时间 2026-09-23 12:07:46（Asia/Shanghai）；Codex 于 12:38:43 左右完成文件大小和哈希回读复核。 |
| 回读字节数 / 重算 SHA-256 | `146869427` / `6426676a6fc0814cedee9c412110e95e3e12a533747201d2d64c8b42cdc8bd3e`，与固定包和平台附件一致。 |
| 程序验签 | `verify-release-artifact-retention.mjs` 对精确包和本机器回执返回 `retention-verified`。 |
| 保留期限 | 预发行说明要求至少保留至 2027-09-23；这是人工保留承诺，不是已验证的平台自动锁期。若用于 S/W，还应持续覆盖线上及前两版。 |

浏览器首次点击下载时自动化等待超时，但 Edge 下载列表随后显示完整文件，本机对实际下载文件重新读取并验签成功；没有用原本的 `target/releases` 包或服务器临时副本冒充下载回读。用户为这次上传手动临时开启了 Edge 中 ChatGPT 扩展的“允许访问文件 URL”；留档完成后 Codex 将该开关关闭，并在扩展详情页重新读取确认其为 `off`。

本回执仅证明 P0-10 对 1.0.158 通过。正式服务器仍为 1.0.154、迁移 40/40；没有运行 1.0.158 的备份、隔离迁移、正式部署、微信开发版上传、体验版绑定或真机交易。上述 S/W/R 需各自独立授权、执行和复核。
