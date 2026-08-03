# 旧版 mall-swarm 模块隔离记录（2026-07-22）

## 当前后端边界

根 Maven Reactor 只保留：

- `mall-common`
- `mall-mbg`
- `mall-distribution`

依赖关系为：`mall-distribution -> mall-mbg -> mall-common`。

商城前端 `mall-shop-web` 和当前管理后台前端 `mall-distribution-admin` 不受本次隔离影响。

## 已隔离内容

- `mall-admin`
- `mall-portal`
- `mall-search`
- `mall-auth`
- `mall-gateway`
- `mall-monitor`
- `mall-demo`
- 旧版 `config` 目录
- 旧版 `docker-compose.yml`

隔离目录：

`/Users/minmatemp/Documents/mall-swarm-legacy-quarantine-20260722-1555`

源码与配置备份：

`/Users/minmatemp/Documents/mall-swarm-legacy-backup-20260722-1555.tar.gz`

备份 SHA-256：

`37e7bb3881f624711b16e627e2871057697df3aa3f7186181ffc3108497614ba`

备份权限为 `600`，只允许当前本地用户读写。备份不包含可重新生成的 Maven `target` 目录。

## 回滚原则

永久删除前保留隔离目录和压缩备份。需要回滚时，将隔离目录中的旧模块、`config` 和 `docker-compose.yml` 移回项目根目录，并从压缩备份恢复隔离前的根 `pom.xml`，然后重新执行完整构建。

本次隔离不涉及数据库表、用户数据、线上目录或线上服务，也没有执行发布。

## 验证门禁

永久删除或发布前必须满足：

1. 根目录干净打包成功，实际运行全部测试。
2. 最终 `mall-distribution` JAR 不包含任何旧业务模块。
3. 两个前端生产构建成功。
4. 两个前端依赖安全扫描无已知漏洞。
5. `scripts/verify-active-module-boundary.sh` 检查通过。
6. 登录服务器只读检查 Java、Docker、systemd、Nginx、监听端口和 Nacos 注册实例。
