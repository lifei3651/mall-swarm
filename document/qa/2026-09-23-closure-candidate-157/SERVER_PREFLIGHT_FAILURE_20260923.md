# 1.0.157 服务器发布前预检中止回执

时间：2026-09-23（Asia/Shanghai）

- 本轮已获授权推进 1.0.157 收口验证版的备份、迁移演练、服务器 S 与微信开发/体验版 W；提审、正式发布和真实资金/关系写入不在本轮授权内。
- 私有留档回读验证过的冻结包 `lingqi-mall-1.0.157-ef9da4bd.tar.gz` 为 146870042 字节，SHA-256 为 `b065fe36c1a4c9a2fc4bc71a2c1a058687cca6e46da88322fee070210a75ed9a`。包与留档回执仅被复制、验签并解压到 `/tmp/lingqimall-closure-157.gMd5un`；包内逐文件 `SHA256SUMS` 全部通过。
- 执行**包内原版** `release-backend.sh --preflight-only` 时，`verify_applied_migration_prefix` 的 SQL 被写为 shell 单引号内的 `\":\"`。反斜杠原样送到 MySQL，返回 `ERROR at line 1: Unknown command '\\"'`，随即报 `database migration count is not the expected successful prefix` 并退出 1。根因是发布脚本 SQL 转义，不是正式库缺迁移。
- 独立只读 SQL 查询得正式库迁移 `40/40`；源码中的唯一错误已修正并补自动合同测试。同组脚本扫描没有第二处 `\":\"`。仅将修正脚本以 `diagnostic-preflight-backend.sh` 名义复制到同一临时目录，**只执行** `--preflight-only`，结果 `release-preflight=passed ... migrations=40:40`；没有使用诊断脚本执行 `--authorize-release`。
- 此次**没有**发布前备份、创建隔离库、执行第 41 条迁移、停服务、替换 JAR/VERSION、静态站发布或微信上传。独立回读仍是后端 `1.0.154`、JAR SHA-256 `786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96`、正式库迁移 `40/40`、四服务 active、health UP。
- 1.0.157 精确包与私有留档保留作失败证据，但**禁止**通过修改包内脚本或绕开验签继续发布。依据收口规则，修复必须分配新版本并重走 C/T、封包、异地留存回读、服务器 S 和微信 W；P0-10 对旧包的通过不继承到新包。预检缺陷纳入 P0-12。
