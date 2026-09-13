# 1.0.140 前端修复发布（2026-09-13）

用户明确要求发版。业务基线 d530f83a，分支 codex/security-membership-payout；开始前工作区干净，pull 成功。

## 范围

- 公开/团队 H5 与小程序更新为 140：常用服务图标、工单六状态无横滑、通栏选择/取消/无订单状态、分享和其他按钮宽度，以及 H5 窄窗口修复。
- 后端及管理后台生产代码没有新增变更，保持已部署139/e6a11c3d；不运行迁移，不启用人工余额提现、银行卡或人工打款。这些仍为待实施规划。
- 后台三张设计未选择，不发布设计示意图为真实功能。小程序仅上传开发/体验版，不提审或发布正式版。

## 门禁

- 公网公开/团队139及SSH后台139/e6a11c3d确认；正式服务器身份VM-4-6-rockylinux，四服务active、healthUP。公网后台一次读取超时，独立SSH直接核对文件成功。
- 复用139静态发布的完整备份/哈希、旧版严格检查、旧资源保留、失败回退及未发布组件/配置保护。140脚本只接受指定139源提交，后端版本仍要求139。
- 原生470与H5 158测试通过；构建、官方编译、补拍和最终发布回执完成后补录。此准备条目不代表已上线。

## 实际回执

- 冻结提交 `78a22078994acf6c7c84524ae66f57f15ee4e2a3`，公开/团队/一体化H5均从该提交构建；三包构建及边界检查通过。原生470、H5 158、同源/工程检查和官方34WXML/36WXSS编译通过。没有重新构建或发布后端和后台。
- 官方隔离模拟器补拍9页/状态：商品、优惠券、直播工作台、我的、工单详情、订单详情、评价、工单填写、地址编辑。关键修复已查看截图，分享实测45×45；证据 `qa/2026-09-13-release-140/`。本轮没有320全页面补拍，也不代表真机和真实资金验收完成。地址保存按钮仍是较窄居中样式，属于后续统一规范待处理项，不把整套UI标记完工。

| 组件 | 发布前完整备份 | 发布后完整备份 | 旧站回退 |
| --- | --- | --- | --- |
| 公开H5 | /opt/lingqimall/backups/full/20260913_123710 | /opt/lingqimall/backups/full/20260913_123722 | /opt/lingqimall/backups/public-h5-140.xlcsdn |
| 团队H5 | /opt/lingqimall/backups/full/20260913_123737 | /opt/lingqimall/backups/full/20260913_123749 | /opt/lingqimall/backups/team-h5-140.3nsc45 |

- 12:37两个静态站发布完成，全文件哈希、入口资源、前后完整备份校验、旧资源保留及受保护组件/配置哈希通过。发布后独立公网version.json均140/78a22078；独立SSH后端139、四服务active、healthUP。未迁移数据库、未变更资金/主题配置。日志 `/private/tmp/lingqi140-shop-deploy.log`、`/private/tmp/lingqi140-team-deploy.log`。
- 正式小程序工程 `/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program` 导出140/78a22078，AppID wxd26e0a4e41df392b、API https://lingqimall.com/api、urlCheck=true，287个文件哈希验证一致。旧139保留 `/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program.previous-1789274237644` 及相邻release.json。
- 12:39微信官方CLI upload返回退出码0及 `✔ upload`，包977493字节（954.6KB），版本1.0.140；结果文件 `/private/tmp/lingqi140-upload-result.json` 只含包大小，不冒充额外后台发布回执。已上传开发代码，未提审/正式发布；本轮未独立确认体验版指向140。
- 隔离QA工程已关闭。开发工具原正式窗口机型显示iPhone12/13(Pro)；本轮未改机型。上传前Mac再次锁屏，已经请求解锁；临时服务端口32227仍待解锁后关闭，不标记已恢复。官方上传无需操作锁屏界面即获得成功回执。
