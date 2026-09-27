# 核心交易应用层 MySQL 收口

日期：2026-09-27（Asia/Shanghai）。基于已推送 `b099dbca` 的后续源码；本轮只修核心链路，不形成新包、不部署、不操作线上业务数据。线上只读复核仍为 **1.0.167 / c40a0e8c / 20260924-closure-1.0.167**，迁移 **41/41**。

## 1. 本轮推进与发现

上轮只在 MySQL 执行财务 SQL；本轮让当前编译后的 Spring 应用服务、事务代理、MyBatis 和连接池直接连接 MySQL，复用同一套交易测试。

发现并复现一项产品缺陷：两个余额支付请求同时到达，行锁/流水幂等使数据库只扣一次款，但后到请求在等待行锁后，普通订单查询仍读取 MySQL `REPEATABLE-READ` 旧快照，成功响应里的订单状态可能为 0（待支付）。原 MyBatis 缓存规避不足以解决数据库快照问题。

- 仅余额支付的事务入口改用 `READ_COMMITTED`，订单/父交易/资产行锁、渠道校验与扣款流水幂等保留；未修改服务器全局隔离级别或全部业务事务。
- 同类调用核对：`ShopWalletController` 是生产代码中该服务的调用入口，无控制器外层事务；同一服务入口的单单/父交易分支均受该隔离设置约束。**本次 MySQL 并发实际跑的是单单余额支付，不能把静态分支覆盖写成父交易并发验收。**
- 原余额并发用例加强为同时检查两个响应、持久订单状态、唯一扣款流水和最终余额。
- 新增同一取消退款两人并发审核，要求一笔成功、一笔明确“售后单已审核”，退款流水、余额回退、库存恢复、财务退款各一次；不能把 SQL 异常当作正常竞争失败。
- 并发提现、售后申请、限购用例改为仅捕获业务拒绝 `ApiException`，不再吞掉任意数据库/程序异常；售后用例改用现行类型 4 和“取消未发货订单”原因，不以禁用的历史类型 1 冒充顾客新入口。
- 新增真实数据库注册/登录用例：开启邀请可绑定，关闭后新邀请注册不写会员或会话、历史关系保留，普通账号仍能注册登录、下单、取消并恢复库存；推广资格独立关闭时不自动激活。

另外修正两项测试可移植性：四处 H2 专有 `DATEADD` 改为绑定时间；商品保障快照比较改为 JSON 结构相等（MySQL JSON 会规范化空白/键顺序）。最初种子导入客户端编码错误造成中文乱码，最终均显式使用 `utf8mb4` 重新导入；这些不是业务缺陷。

## 2. 隔离与数据边界

沿用用户只读结构/迁移登记授权，仅导出正式库 99 表结构和 41 条迁移登记，流入本机 `mysql:8.4.10` 容器。未导出客户、订单或资金行，也不保存正式 dump 文件。导入时会员/订单为空；仓库原版迁移器在副本完成 41→44，校验 44/44。

- MySQL 容器：`mall-core-closure-20260927`，`--network none`，端口映射 `{}`，数据目录 1 GiB tmpfs，`--rm`。
- 测试 JVM：现有 JRE 17.0.19 镜像 `lingqi_round17_full-mall-distribution:latest`，**覆盖默认启动命令**运行 Maven 离线 `surefire:test`，读取当前编译的 `target/classes` / `target/test-classes`；没有运行镜像内旧 app.jar。
- JVM 仅共享无网络 MySQL 的回环网络，不挂载 SSH 或线上配置；Maven 依赖只读挂载。无短信、支付或物流提供方网络调用。
- `PerformanceServiceTest` 用独立结构副本和仓库合成种子；钱包用例使用另一份同结构干净种子，避免非事务测试遗留行污染下一轮。`CoreRegistrationClosureTest` 自身事务回滚。
- 系统默认隔离级别回读仍为 `REPEATABLE-READ`。仅指定服务事务使用 `READ_COMMITTED`。

复跑参数（前提：已确认是无网络、无端口的临时结构副本；严禁把此种子导入线上库）：

```text
宿主编译：./mvnw -pl mall-distribution -DskipTests test-compile
种子：mysql --default-character-set=utf8mb4 ... < mall-distribution/src/test/resources/db/data-h2.sql
容器离线 Maven：-o -pl mall-distribution -Dtest=<以下测试类> surefire:test
覆盖 spring.datasource.url=jdbc:mysql://127.0.0.1:3306/<临时库>?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
覆盖 spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
覆盖 spring.datasource.username=root、空 password、spring.sql.init.mode=never
测试 JVM：UTC 时区，显式加载本地 byte-buddy-agent 1.17.8（JRE 无动态 attach 工具）
```

## 3. 实际结果

| 当前应用连接 MySQL 的测试 | 通过 | 直接覆盖 |
|---|---:|---|
| `PerformanceServiceTest` | 58/58 | 订单/支付内部状态、发货收货、取消/退货退款/换货、退款处理中不记已到账、奖金回退、成本/剩余款归集结算及退款冲销、财务逐单/汇总 |
| `ShopWalletServiceTest` | 13/13 | 余额支付、余额不足、并发重复支付、提现/退回、并发不透支、售后/限购竞争、支付密码、并发退款和库存/账务回读 |
| `CoreRegistrationClosureTest` | 1/1 | 邀请开/关、历史关系保留、拒绝零会员/会话写入、普通注册登录/下单取消 |
| 合计 | **72/72，失败/错误/跳过 0** | 应用层服务集成，不是浏览器/真机/渠道验收 |

最后两轮 Maven 分别返回 `BUILD SUCCESS`：58 项于 00:47:13 UTC 完成，14 项于 00:46:47 UTC 完成。最终日志本机保留：

| 本机日志 | SHA-256 |
|---|---|
| `/tmp/mall-core-mysql-orders-final.log` | `d7af1910aa4c2cf9cfff2c615bde647ca2705617af8037a40cd6857b9ba5ef23` |
| `/tmp/mall-core-mysql-verified.log` | `c8e3bdfdffbcec34fcf635a175b367c485b0d82d754166eb6edecd8b33297acc` |

日志哈希用于识别本机回执，不代表耐久产物留档。未提交包含环境属性的 Surefire XML。自动测试中的缺归集账户/失败路径日志属于相应合成场景，不能当作线上新增报错，也不能用测试总数代替功能矩阵。

并发退款独立 SQL 回读（合成 user 1009）：初始余额 500，订单实付 299，一条扣款、一条余额退款、一条财务退款，最终余额 500；订单状态 4，财务支付 299 / 退款 299 / 净支付 0 / 奖金 0 / 利润 0。库存回到下单前值由测试断言验证。系统资金归集及冷静期结算由另外的 Performance 场景验证，不冒称本钱包场景覆盖了全部归集账户。

同一最终源码 `./mvnw test`：后端 **905**、公共模块 **17**，失败/错误/跳过均为 0，08:48:25 +08:00 返回 `BUILD SUCCESS`。本机 `/tmp/mall-core-full-regression.log` SHA-256 为 `1eabdf2c1c5d9950edc84f60115ed6b6ed44f3a44be5c6f3ea2259c6898b85b9`。本轮没有前端改动，未重复前端构建；统一候选仍须同版跨端门禁。

测试结束后停止精确目标 `mall-core-closure-20260927`，容器自动移除、按名查询为空；临时结构及合成数据随 tmpfs 清理，可从结构和测试夹具重建。未删除任何用户业务数据。正式库再次只读核对仍 41/41，未执行生产迁移。

## 4. 剩余边界

此次消除了“尚未连接 MySQL 跑核心服务”的证据缺口，并修复复现到的余额并发状态问题；不等于 P0 全部关闭。还需最终同提交跨端回归/候选、受控服务器和微信体验版分发，以及用户恢复后同版角色/界面和最少受控支付退款全流程验收。渠道成功仍需真实提供方回执；不扩展营销组合、ERP 厂商适配、客户真实资料或大型电商能力。
