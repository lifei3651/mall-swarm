# 1.0.151 商城共用界面规范发布回执

发布时间：2026-09-20（Asia/Shanghai）

## 发布结果

- 管理后台、公开商城 H5、团队 H5 已发布为 `1.0.151`，三站公网 `version.json` 均已核对版本、应用标识、构建编号和冻结提交。
- 微信小程序 `1.0.151` 已通过微信开发者工具官方 CLI 上传开发版，输出 `✔ upload`，上传包大小为 `1,538,450` 字节。
- 本轮没有重新发布后端、没有数据库迁移、没有修改订单、支付、退款、奖金或客户数据。服务器后端继续保持 `1.0.149`，`lingqimall-distribution`、`nginx`、`mysqld`、`redis` 均为 active，健康检查为 UP。
- 小程序只完成开发版上传，尚未提交微信审核、尚未正式发布，也没有代替用户确认体验版指向。

## 发布内容

- 小程序与 H5 建立共用商城界面规范，统一卡片、主按钮、次按钮、危险按钮、辅助按钮、价格、状态、客服入口和操作组；订单、商品、购物车、账户与商城说明等页面不再各自复制颜色和尺寸规则。
- 管理后台“品牌与主题”支持主按钮和价格强调色分别配置，也支持一键统一跟随主题色；微信/支付宝、成功、警告和危险等语义色不被品牌色覆盖。
- 管理后台、公开商城 H5、团队 H5 和小程序沿用同一商城业务功能与接口，本轮只统一显示和配置协议，不改变交易规则。

## 冻结提交与构建

- 三个网页静态站冻结提交：`e25c760c4428fcc65956847adbf8bbb252f76db3`。
- 小程序冻结提交：`6e3dd1800c8c839fe3894e3a22294133a27a1cba`。该提交仅在网页冻结提交之上补齐小程序 `package.json` 与锁文件的 `1.0.151` 版本标识，并记录门禁结果；商城功能代码不变。
- 管理后台 44 个测试文件共 203 项、小程序 510 项、H5 173 项全部通过；小程序工程检查、共用界面规范门禁与 H5 同源检查通过。
- 管理后台、公开商城 H5、团队 H5、一体化 H5 的生产构建均通过；一体化 H5 只构建验收，没有单独部署。现有 Vite `hls` 分包大于 500 KB 的提示保留，不是本轮新增错误。
- 320×800 实际源码验收中，视口、页面和文档宽均为 320 像素，无横向溢出和控制台错误；客服内容组边界完整。

## 服务器发布与回滚

- 管理后台：发布前完整备份 `/opt/lingqimall/backups/full/20260920_150747`，发布后完整备份 `/opt/lingqimall/backups/full/20260920_150800`，旧站回滚目录 `/opt/lingqimall/backups/admin-151.MAxi0P`。
- 公开商城 H5：发布前完整备份 `/opt/lingqimall/backups/full/20260920_150823`，发布后完整备份 `/opt/lingqimall/backups/full/20260920_150835`，旧站回滚目录 `/opt/lingqimall/backups/public-h5-151.hwttnk`。
- 团队 H5：发布前完整备份 `/opt/lingqimall/backups/full/20260920_150856`，发布后完整备份 `/opt/lingqimall/backups/full/20260920_150908`，旧站回滚目录 `/opt/lingqimall/backups/team-h5-151.wXWSx6`。
- 三次发布都校验了候选归档与逐文件哈希，并保护后端、配置和非目标站点；发布后版本、入口资源、界面探针、前后备份、服务状态与健康检查全部通过。

## 小程序正式目录与官方回执

- 固定正式工程：`/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program`。
- 清单：`/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program.release.json`；共 290 个文件，缺失 0、额外 0、哈希不一致 0。
- AppID 为 `wxd26e0a4e41df392b`，正式 API 为 `https://lingqimall.com/api`，合法域名校验保持开启。
- 旧 `1.0.149` 正式目录保留为 `/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program.previous-1789888362769`，并保留对应清单，可用于重新核对或恢复。
- 官方 CLI 回执：`/private/tmp/lingqi151-upload-result.json`；包信息：`/private/tmp/lingqi151-upload-info.json`；导出回执：`/private/tmp/lingqi151-prepare-result.json`。
- 首次导出因小程序自身包版本仍为 `1.0.149` 被一致性门禁主动终止，旧正式目录没有被覆盖；补齐版本并重新通过 510 项测试和工程检查后才生成、上传 `1.0.151`。
- 上传时临时开启微信开发者工具服务端口 `32227`，上传完成后已关闭；`32227`、`9420`、`4176` 均确认无监听。

## 公网最终核验

- `https://lingqimall.com/admin/version.json`：`1.0.151 / admin / e25c760c...`。
- `https://lingqimall.com/version.json`：`1.0.151 / storefront-public / e25c760c...`。
- `https://www.lingqimall.com/version.json`：`1.0.151 / team-h5 / e25c760c...`。
- 服务器 `/opt/lingqimall/VERSION` 仍为 `1.0.149`，与本轮不发布后端的边界一致。
