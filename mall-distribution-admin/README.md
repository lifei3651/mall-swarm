# 分销分佣系统管理后台

基于 Vue 3 + Element Plus 开发的分销分佣系统管理后台。

## 技术栈

- **Vue 3** - 前端框架
- **Element Plus** - UI组件库
- **Vue Router** - 路由管理
- **Pinia** - 状态管理
- **Axios** - HTTP请求
- **ECharts** - 图表库
- **Vite** - 构建工具

## 功能模块

### 1. 控制台
- 代理总数统计
- 今日业绩统计
- 待结算佣金统计
- 待审核提现统计
- 业绩趋势图
- 代理等级分布图

### 2. 代理管理
- 代理列表（搜索、筛选、分页）
- 代理详情（基本信息、账户信息、业绩概览）
- 添加代理
- 代理关系树
- 代理切线

### 3. 佣金管理
- 佣金记录（搜索、筛选、分页）
- 佣金规则配置
- 佣金结算（单个结算、批量结算）

### 4. 业绩统计
- 业绩概览（个人业绩、团队业绩、分层业绩）
- 下属贡献排名
- 业绩排行榜

### 5. 提现管理
- 提现记录（搜索、筛选、分页）
- 提现审核（通过、拒绝）
- 确认打款

### 6. 批量导入
- 导入代理（Excel文件）
- 导入订单（Excel文件）
- 导入结果查看

### 7. 账户管理
- 账户列表
- 账户详情（佣金信息、推广信息、佣金记录、提现记录）

## 快速开始

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

### 构建生产版本

```bash
npm run build
```

## 项目结构

```
src/
├── api/                    # API接口
│   ├── account.js
│   ├── agent.js
│   ├── commission.js
│   ├── import.js
│   ├── performance.js
│   └── withdraw.js
├── assets/                 # 静态资源
│   ├── logo.svg
│   ├── logo-small.svg
│   └── styles.scss
├── components/             # 公共组件
│   └── Layout.vue
├── router/                 # 路由配置
│   └── index.js
├── store/                  # 状态管理
│   └── index.js
├── utils/                  # 工具函数
│   └── request.js
├── views/                  # 页面组件
│   ├── dashboard/          # 控制台
│   ├── agent/              # 代理管理
│   ├── commission/         # 佣金管理
│   ├── performance/        # 业绩统计
│   ├── withdraw/           # 提现管理
│   ├── import/             # 批量导入
│   └── account/            # 账户管理
├── App.vue                 # 根组件
└── main.js                 # 入口文件
```

## 接口说明

所有接口都通过 `src/utils/request.js` 中的 axios 实例发送请求，接口地址配置在 `vite.config.js` 中的代理配置。

### 接口列表

- `/distribution/agent/*` - 代理管理接口
- `/distribution/commission/*` - 佣金管理接口
- `/distribution/performance/*` - 业绩统计接口
- `/distribution/withdraw/*` - 提现管理接口
- `/distribution/import/*` - 批量导入接口
- `/distribution/account/*` - 账户管理接口

## 开发说明

### 添加新页面

1. 在 `src/views/` 下创建页面组件
2. 在 `src/router/index.js` 中添加路由配置
3. 在 `src/api/` 中添加接口调用

### 修改主题

修改 `src/assets/styles.scss` 中的样式变量。

### 添加新接口

1. 在 `src/api/` 下创建接口文件
2. 使用 `src/utils/request.js` 中的 axios 实例发送请求
