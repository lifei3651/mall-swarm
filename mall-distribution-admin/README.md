# 灵启商城管理后台

长沙灵启软件开发有限公司开发和维护的商城运营管理后台，基于 Vue 3、Element Plus、Pinia、Vue Router、Axios、ECharts 和 Vite 构建。

Copyright © 2026 长沙灵启软件开发有限公司。保留所有权利。

## 主要能力

- 智慧经营工作台与经营报表。
- 会员、等级、团队关系、移线和账户管理。
- 商品、SKU、分类、库存、运费、发退货地址、秒杀和复购管理。
- 订单、物流、客服备注、退款与售后处理。
- 奖金计算、结算、追回、钱包、提现和财务核对。
- 商城品牌、视觉、公告、协议、资质、规则、ERP 和客户交付检查。
- 管理员、权限、操作日志、审计和风控配置。

## 开发命令

```bash
npm install
npm run dev
npm test
npm run build
```

接口统一通过 `src/utils/request.js` 访问，管理后台使用 Secure、HttpOnly Cookie 会话，写请求必须携带后台客户端来源标识。具体接口与维护口径见根目录的 [技术维护与接口说明](../document/TECHNICAL_MAINTENANCE_API_MANUAL.md)。

版权和开源许可遵循仓库根目录的 [版权声明](../COPYRIGHT.md)、[NOTICE](../NOTICE) 与 [LICENSE](../LICENSE)。
