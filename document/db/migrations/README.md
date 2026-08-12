# 数据库版本迁移目录

这里只存放从本规则启用后新增的增量迁移，文件名必须是：

`VYYYYMMDDHHMM__lowercase_description.sql`

例如：`V202608111530__add_client_error_index.sql`。

已有的 `document/sql` 与各模块历史 SQL 视为基线，不会自动执行，防止在现有客户数据库重复建表或重复改数据。

## 当前待发布迁移

- `V202608121235__tenant_after_sale_window.sql`：为商城客户增加售后期限起算模式与有效天数；默认“签收后 7 天”，可切换为兼容的“下单后起算”。发布前须先核对迁移登记和线上实际字段，禁止凭日志猜测或重复执行。
