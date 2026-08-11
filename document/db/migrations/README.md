# 数据库版本迁移目录

这里只存放从本规则启用后新增的增量迁移，文件名必须是：

`VYYYYMMDDHHMM__lowercase_description.sql`

例如：`V202608111530__add_client_error_index.sql`。

已有的 `document/sql` 与各模块历史 SQL 视为基线，不会自动执行，防止在现有客户数据库重复建表或重复改数据。
