# 1.0.141 商城体验与性能修复发布（2026-09-14）

用户明确要求按“后端、小程序、H5”顺序发布。线上基线为后端1.0.139、数据库迁移36/36、公开与团队H5 1.0.140；四项服务均为active且健康检查UP。

## 范围与边界

- 后端先发布：商品列表缩略图与销量升序；本版无新增数据库迁移，不改变优惠券、结算、奖金、实名、支付或提现开关。
- 小程序上传开发代码1.0.141，不自动提审、不自动正式发布。
- 随后发布公开商城H5与团队H5；管理后台和一体化H5不在本次部署范围。
- 发布前后均做完整备份、哈希校验、服务健康与受保护配置检查；实际回执完成后补录。

## 实际回执

- 后端730项、小程序495项、H5 168项测试通过；小程序工程检查及公开/团队/一体化H5普通构建与边界检查通过。
- 冻结源码提交`b33aa5762beda4c23bef5f88fd06ad8828b7f49a`，已推送到`codex/security-membership-payout`。后端JAR SHA256为`d36b8ee21c75b081dc124b3deecc5424b92e20276be71894c7c0bac926a1a426`；公开/团队H5均带冻结提交和独立构建编号重新构建并通过边界检查。
- 09:21–09:22后端141发布成功。发布前完整备份`/opt/lingqimall/backups/full/20260914_092118`，发布后完整备份`/opt/lingqimall/backups/full/20260914_092207`，回退目录`/opt/lingqimall/backups/mini-backend-141.bg5bDg`。隔离恢复、数据快照、配置/静态站哈希、运行开关、启动日志和公网接口均通过；迁移仍为36/36，无数据迁移，支付、提现和通知外部通道未开启。独立复核后端141、JAR哈希一致、四服务active、health UP，`salesAsc`与首页接口正常。
- 正式小程序工程导出至`/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program`，版本141/冻结提交一致，290个文件逐项哈希通过；旧140保留为`/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program.previous-1789349007834`。微信官方CLI返回退出码0和`✔ upload`，上传包988968字节；只上传开发代码，未提审、未正式发布，也不声明体验版已自动切换。结果文件`/private/tmp/lingqi141-upload-result.json`。上传后关闭正式工程，服务端口32227已在安全设置关闭并确认无监听。
- 09:25公开商城H5141发布成功：发布前/后完整备份`/opt/lingqimall/backups/full/20260914_092531`、`/opt/lingqimall/backups/full/20260914_092543`，回退目录`/opt/lingqimall/backups/public-h5-141.nlYtNc`。09:26团队H5141发布成功：发布前/后完整备份`/opt/lingqimall/backups/full/20260914_092623`、`/opt/lingqimall/backups/full/20260914_092635`，回退目录`/opt/lingqimall/backups/team-h5-141.lqaIXD`。两站公网`version.json`均为141/冻结提交，入口和全部引用资源通过；旧不可变资源保留。
- 最终交叉核验：后端、公开H5、团队H5均为141；数据库36/36、四服务active、health UP；管理后台保持139/e6a11c3d，未部署；一体化H5只做本地构建，未上线。本轮未进行微信真机交易、头像、支付、提现或客服外部通道验收。
