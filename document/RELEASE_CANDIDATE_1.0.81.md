# 灵启商城 1.0.81 正式候选发布准备

更新时间：2026-08-24（Asia/Shanghai）

状态：正式候选已于 2026-08-24 完成生产发布与复验

生产现状：公开商城、团队 H5、管理后台和后端均为 `1.0.81`

## 冻结身份

- 产品基座：`/Users/minmatemp/Documents/mall-swarm-app-h5`
- 分支：`codex/app-h5-split`
- 业务冻结提交：`3ce3e11fa679786723be1054fafc0906248a198b`
- 版本：`1.0.81`
- 构建号：`20260824-1110-1.0.81`
- 生产拓扑：公开商城、团队 H5、管理后台拆分部署；一体化 H5 仅作为同源可选产物封包，不在本次拆分发布中启用。

业务产物只认以上冻结提交。后续发布准备文档或脚本的提交不改变业务构建身份。

## 正式候选包

候选目录：

`/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110`

| 组件 | 绝对路径 | 字节数 | SHA-256 | 版本 / 构建身份 |
| --- | --- | ---: | --- | --- |
| 后端 | `/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110/mall-distribution.jar` | 156,529,980 | `6cf2b116750948be0f1c06d4b3c19f721406cd3e9eb51587c34a360ddcde2d75` | `1.0.81` / `20260824-1110-1.0.81` / `3ce3e11...` |
| 管理后台 | `/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110/admin.tar.gz` | 2,533,685 | `fdb109e4240e7b6ae6114cc033a17be8c68c0c4b728d1c37c5a1ad164dd60051` | `1.0.81` / `20260824-1110-1.0.81` / `3ce3e11...` |
| 公开商城 | `/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110/shop.tar.gz` | 853,062 | `2efab5d9116c4f19d37f034608a8b7996399496c1623dc3240b3cd8dac4bbb51` | `1.0.81` / `20260824-1110-1.0.81` / `3ce3e11...` |
| 团队 H5 | `/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110/team.tar.gz` | 567,571 | `67cfcaa1a55f5bbc0b57d1216b396184f38954fcb9d0212452e12ad976a7f30b` | `1.0.81` / `20260824-1110-1.0.81` / `3ce3e11...` |
| 一体化 H5（可选、不启用） | `/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110/integrated.tar.gz` | 892,828 | `43aa1921b5bea0520838763b6e35c17dd80b349c47eaa10c4bc48fb6789446e9` | `1.0.81` / `20260824-1110-1.0.81` / `3ce3e11...` |

完整传输包：

- 路径：`/Users/minmatemp/Documents/mall-swarm-app-h5/target/release-candidates/lingqimall-v1.0.81-20260824-1110.tar.gz`
- 字节数：144,153,598
- SHA-256：`726ecd8cb046e48a6d25b4663ff5720cd689ae359fd8068b125a8c29269c57f2`
- 内含五类业务产物、`VERSION`、`RELEASE_MANIFEST.json`、33 项 `SHA256SUMS`、21 项迁移总账、备份/迁移工具、拆分版 Nginx 配置和版本专用 `release.sh`。
- `release.sh`：17,028 字节，SHA-256 `13083e895736c0b741e621716cc0d07235e39a46a7d2fd66153f17c64e19e01a`。

## 已完成校验

- 远程分支同步完成；准备开始时本地与 `origin/codex/app-h5-split` 均指向冻结提交，工作区干净。
- 线上三个公开版本清单只读核验为 `1.0.80`，线上 Git `f2ce2142188b8a023c27c9412144423ac468c090`，构建号 `20260823-1631-1.0.80`。
- 公共模块 14/14、分销后端 440/440、登录短信发送与验证码消费专项 17/17、商城 81/81、管理后台 121/121 通过。
- 后端正式打包、公开商城、团队 H5、一体化 H5、管理后台生产构建成功；公开商城与一体化 H5 边界门禁通过。
- 四个前端包内的 `version.json` 均为 `1.0.81`、冻结 Git 和统一构建号；一体化包标记为 `integrated-h5`。
- JAR ZIP 完整性、四个前端 gzip/tar 完整性、完整传输包 gzip 完整性和 33 项 SHA-256 清单均通过。
- 前端包不含 source map；候选包文件名扫描未发现 `.env`、`.git`、私钥、证书密钥库或 source map；文本扫描未发现常见私钥/访问令牌特征。
- 21 项迁移文件与当前线上已登记的 21/21 数量一致；`1.0.81` 没有新增数据库迁移。
- 版本专用发布脚本及备份、迁移脚本均通过 Shell 语法检查；脚本固定校验上一版本 `1.0.80`、候选版本、冻结 Git、构建号、JAR 哈希和迁移数，不满足即停止。

## 发布前备份对象

实际发布必须先生成新的全量备份并完成恢复性校验，不能复用旧备份。备份对象包括：

1. MySQL `mall_distribution`：单事务导出，包含 routines、triggers、events，生成 `database.sql.gz` 并执行 gzip 校验。
2. `/opt/lingqimall/uploads`、`/opt/lingqimall/config`、当前后端 JAR。
3. 当前管理后台、公开商城、团队 H5 静态目录。
4. 后端 systemd 服务、备份 service/timer 和备份程序。
5. 实际存在的 systemd drop-in、Nginx 主配置/站点配置、安全限制、TLS 配置及证书目录。
6. 字段加密配置 `/opt/lingqimall/config/data-encryption.env` 与对应 systemd drop-in；发布前后必须保持 SHA-256 不变。
7. 发布脚本另建短期回滚点，单独保存当前 `mall-distribution.jar`、`VERSION`、三端静态目录、两份 Nginx 配置和当前备份程序。

备份验收必须同时满足：`SHA256SUMS` 全部通过、数据库 gzip 可解压、文件包 tar 清单可读取、JAR/三端首页/Nginx/加密配置等关键对象确实在包内、备份后核心业务计数不变。

## 受控发布顺序

以下步骤仅在用户明确授权发布后执行；本次准备没有执行任何一步生产写操作。

1. 再次只读核验线上仍为 `1.0.80`，四项服务 active、Redis `PONG`、后端健康 `UP`；核对 21/21 迁移、失败 0、核心计数、加密状态和无待处理奖金/ERP/移线/超时订单。
2. 将完整候选包置于固定临时目录，先校验包内 33 项 SHA-256、候选身份、前端清单和 JAR 哈希。
3. 生成并复验发布前完整备份；备份失败、缺对象或核心计数变化时立即停止。
4. 在独立临时目录解压三端拆分静态资源并校验版本；一体化 H5 只校验、不切换。
5. 停止后端，运行幂等迁移总账。由于本版无新增迁移，结果必须仍为 21/21、失败 0；随后替换 JAR 与 `VERSION`，启动并等待健康 `UP`。
6. 核对核心计数、敏感字段密文状态和加密配置哈希，再原子切换管理后台、公开商城、团队 H5 静态目录。
7. 安装已冻结的拆分 Nginx 配置，先执行配置校验，成功后才 reload；任何失败进入有界回滚。
8. 完成线上验收清单后生成发布后完整备份并复验；最后再次核对服务、日志、JAR 哈希、迁移、核心计数和加密配置。

## 回滚点

- **生产未变更前**：发布前完整备份是数据库、上传、配置、程序、静态资源和系统配置的恢复基线。
- **后端切换前**：短期回滚目录保留旧 JAR 与旧 `VERSION`；启动或健康检查失败时恢复旧包并启动原服务。
- **静态资源切换前**：三端旧目录整体移入回滚目录；任一页面或资源异常时原子恢复旧目录。
- **Nginx reload 前**：保留两份线上原配置；配置检查或 reload 失败时恢复原配置并重新校验。
- **数据库边界**：本版无新增迁移，正常发布不得改变迁移数或业务数据。若出现任何意外数据库变化，立即停止自动流程，保留现场，由人工依据已验证数据库备份决定恢复，禁止自动覆盖生产数据。
- **外部配置边界**：字段加密密钥、支付、短信、直播、数据库凭证和 TLS 证书均沿用服务器现状，不从候选包生成或覆盖。

## 线上验收清单

- [x] 公开商城、团队 H5、管理后台 `version.json` 均为 `1.0.81`、冻结 Git 和统一构建号；一体化 H5 未被意外启用。
- [x] 三个入口及其入口资源返回 200，公开商城和团队 H5 首页 API 正常。
- [x] 后端健康 `UP`，Nginx、MySQL、Redis、分销服务均 active，Redis `PONG`，启动后无新增严重错误。
- [x] `/api/shop/home`、直播广场、新品速递返回正常；公开商品接口不暴露成本、BV、安全库存、结算及商户内部字段。
- [x] 使用非法手机号对 `/api/sms/send/login` 做无费用探测，返回正确手机号校验提示且不发送真实短信。
- [x] 浏览器确认：公开商城“验证码登录”不显示图形验证码并走登录短信；注册显示图形验证码并走注册短信；密码登录显示图形验证码。团队 H5 与一体化流程未发生拓扑或行为变更。
- [x] 未登录后台接口为 401；Actuator、Swagger、`.env`、`.git` 等敏感路径对公网为 404。
- [x] CSP、Permissions-Policy、HSTS 等安全响应头存在。
- [x] 迁移保持 21/21、失败 0；核心业务计数发布前后一致；待处理奖金、ERP、移线和超时订单为 0。
- [x] 明文敏感字段计数为 0；加密环境文件和 systemd drop-in 的 SHA-256 发布前后一致。
- [x] 发布前、发布后两份完整备份均通过哈希、压缩和关键对象清单复验。

## 发布结果

`1.0.81` 已按冻结候选成功发布。发布前备份为 `/opt/lingqimall/backups/full/20260824_112842`，发布后备份为 `/opt/lingqimall/backups/full/20260824_112914`；两份备份均完成独立复验。发布脚本返回 `release-success`，未触发回滚；核心计数、迁移总账、明文敏感字段计数和加密配置哈希均保持门禁要求。一体化 H5 未启用，服务器临时候选传输包已在验签和发布完成后清理，本机正式候选继续保留。
