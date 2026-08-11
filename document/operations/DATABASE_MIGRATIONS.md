# 数据库迁移执行规范

## 目标

`scripts/db-migrate.sh` 为后续数据库结构变更建立统一的版本、校验和与执行记录。它不会自动重放历史 SQL，也不会自动连接生产环境。

## 新增迁移

1. 在 `document/db/migrations` 新建 `VYYYYMMDDHHMM__说明.sql`。
2. 已执行的迁移文件禁止修改；需要调整时必须增加一个更高版本的新文件。
3. SQL 尽量保持幂等并避免混入测试数据、密码或密钥。

## 检查与执行

仅查看本地迁移计划，不需要数据库连接：

```bash
scripts/db-migrate.sh plan
```

连接目标数据库时，通过当前终端临时提供 `DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`、`DB_NAME`，再执行：

```bash
scripts/db-migrate.sh status
scripts/db-migrate.sh apply
```

脚本使用权限为 600 的临时客户端配置，密码不会出现在命令行参数中，退出时自动清理。

## 安全规则

- 正式环境执行前必须完成数据库全量备份。
- `apply` 同时使用本机锁和 MySQL 跨主机锁，同一目标数据库只允许一个迁移任务运行。
- 版本号重复、校验和变化或历史失败记录都会立即停止，禁止强行跳过。
- MySQL 的部分 DDL 会自动提交；出现失败记录后必须先人工核对数据库状态，不能直接删除历史记录重跑。
- 历史 `document/sql` 文件不在自动迁移范围内，由交接报告记录其人工执行情况。
