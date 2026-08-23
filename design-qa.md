# 前端重点流程视觉验收

- Source visual truth:
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-7619d5ea-0865-40e0-b4f1-7c8e69cc4e44.png`
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-b494a76f-5de3-4cb4-98a9-4af6e82f4231.png`
- Implementation screenshots:
  - `document/qa/20260808-invite-dialog-mobile.jpg`
  - `document/qa/20260808-after-sale-quantity-error-mobile.jpg`
  - `document/qa/20260808-after-sale-reason-error-mobile.jpg`
- Viewport: 390 × 844 CSS px，device scale factor 1。
- Pixel dimensions: 三张实现截图均为 390 × 844；参考图分别为 1206 × 2622 和 630 × 890。参考图按可见内容与交互状态比较，不以浏览器外壳和像素密度差异作为问题。
- State: 已登录会员；个人中心邀请弹窗打开；售后数量为 0 的错误态；售后数量有效但未选择原因的错误态。

## Full-view comparison evidence

- 邀请信息由原来的多个纵向卡片收口为一个居中弹窗，二维码、邀请码、注册账号数、正式会员数和复制邀请链接均在一个手机视口内完成查看与操作；个人中心保持在遮罩后方，符合“点击邀请后再展示”的目标。
- 退款申请沿用原有商品卡、类型卡、原因选择和预计退款结构，不改变业务路径；数量和申请原因的错误分别出现在对应区域，并自动滚动到当前错误，不再把错误堆到页面最底部。
- 页脚仅移除非必要的“常见问题”固定入口，继续保留交易与售后、联系客服、用户协议、隐私政策及备案信息。

## Focused region comparison evidence

- 邀请弹窗：二维码清晰，无压缩模糊；邀请码与复制操作同一行；两项邀请数据并列展示；关闭按钮、遮罩和主操作层级清晰。
- 退款数量：标题带红色必填星号，数量 0 时商品区红框且下方显示“退款商品数量不能为 0，请至少选择 1 件商品”。
- 申请原因：标签带红色必填星号，未选择时选择框红框，并在控件下方显示“请选择申请原因”。

## Required fidelity surfaces

- Fonts and typography: 继续使用商城现有字体栈和字号层级；标题、邀请码、统计数字与错误文案权重区分明确，无异常换行。
- Spacing and layout rhythm: 弹窗在 390 × 844 视口内完整展示，区块间距、圆角和按钮高度沿用商城现有设计语言；退款错误与字段保持紧邻。
- Colors and visual tokens: 主色、淡红错误背景、红色边框和正文灰色均复用商城变量；错误状态对比度明显。
- Image quality and asset fidelity: 使用真实二维码生成结果和现有商品图片，未使用占位图、手绘图形或近似资源替代。
- Copy and content: 邀请数据名称与用户要求一致；退款提示直接说明缺失项及修复方式；页脚保留面向交易、联系、协议、隐私和备案的必要入口。

## Findings

未发现影响本次邀请、退款校验和页脚信息架构的 P0、P1 或 P2 问题。

## Comparison history

- 初始问题：邀请页信息分散且需要长距离滚动；退款数量为 0 或原因缺失时错误出现在页面底部，触发位置不明确。
- 修复：邀请信息改为单卡组件和个人中心弹窗；退款必填项增加星号、字段本地错误、红色状态和自动滚动定位；页脚移除“常见问题”固定入口。
- Post-fix evidence: `document/qa/20260808-invite-dialog-mobile.jpg`、`document/qa/20260808-after-sale-quantity-error-mobile.jpg`、`document/qa/20260808-after-sale-reason-error-mobile.jpg`。

## Primary interactions tested

1. 个人中心点击“邀请”打开弹窗，关闭按钮可见，背景滚动被锁定。
2. 数量减到 0 后提交，页面滚动到商品数量并显示本地错误。
3. 数量恢复为 1 后提交，页面滚动到申请原因并显示本地错误。
4. 页脚不再显示“常见问题”，其余服务与合规入口保持可访问。
5. 浏览器控制台未出现阻断本次交互的错误。

## 订单详情与售后申请结构验收（2026-08-08）

- Source visual truth: `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-e35bafda-3c8b-4305-b354-50ac22f3322c.png`
- Target viewport: 390 × 844 CSS px。
- 已通过源码、36 项前端回归测试和生产构建确认：物流摘要位于商品信息前；已签收订单显示“已签收”；商品区不再重复显示“金额”标题；订单状态、商品金额、实付金额、支付方式保持直接可见；订单号、创建时间、付款时间、发货时间和运费合并为默认收起的“订单信息 共5项”。
- 售后申请状态下，“我的订单”入口、重复的“申请退款 / 售后”标题和页面末尾订单编号均已隐藏或移除。
- 视觉浏览器已用 390 × 844 手机视口成功访问当前 Mac 的本地商城；继续进入订单详情需要登录，而当前登录页启用了图形验证码。本轮未代替用户处理验证码，因此无法生成可信的登录后 post-fix 截图或执行订单信息真实点击展开对比。没有用静态占位图冒充验收结果。

该历史验收项结果：blocked（登录图形验证码阻断了登录后视觉验收；代码测试与生产构建均已通过）

## 商城 LOGO 与视觉装修工作台验收（2026-08-23）

- Source visual truth:
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-d226f9f1-1b22-46e0-b506-6880de4d6739.png`
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-f206534d-f5f4-4cf8-a9d3-416e18eeccaa.png`
- Implementation evidence: Codex 应用内浏览器实际渲染本次组件，使用真实线上品牌 LOGO 资源并在本地隔离数据下打开“商城视觉装修工作台”。
- Viewport: 1280 × 720 CSS px。
- State: 品牌视觉编辑态，真实 LOGO 已加载，主题选择、商城名称、LOGO、主题色、手机预览和底部操作同时可见。

### Full-view comparison evidence

- 原界面在 2732 × 1510 截图中仍需要页面级滚动，弹窗高度、手机模型和留白均过大；修复后 1280 × 720 下弹窗边界为 `top=12 / bottom=708 / height=696 / width=1120`，页面无纵向溢出。
- 标题、草稿说明、品牌编辑表单、客户手机预览、取消和保存发布按钮均在一个视口中完整可见；内容较多的其他装修模块保留左侧区域内部滚动，不推动整个弹窗超出屏幕。
- 手机预览保持原有信息层级和真实商城样式，仅压缩到适合后台并排校对的尺寸，不改变客户前台实际页面尺寸。

### Focused region comparison evidence

- LOGO：线上资源返回 `image/png`，浏览器检测 `complete=true`、原图宽度 896；后台侧栏、登录页、表格、上传编辑器和手机预览均增加相对地址规范化及加载失败回退。
- 操作区：底部“取消 / 保存发布”两按钮在 720 高度视口内均完全可见。
- 手机预览：实际高度 403，底边 619，未遮挡弹窗底部操作区；预览内部继续允许独立滚动查看后续商品模块。

### Required fidelity surfaces

- Fonts and typography: 沿用 Element Plus 和现有后台字体层级，仅压缩标题、辅助文案和手机预览字号，无异常换行。
- Spacing and layout rhythm: 弹窗统一 12px 顶部安全边距，标题/正文/页脚使用紧凑间距，左右编辑区和预览区保持清晰分栏。
- Colors and visual tokens: 沿用现有蓝灰后台与商城主题色，不引入新的颜色体系。
- Image quality and asset fidelity: 使用真实商城 LOGO，不使用占位图或近似图形；加载失败时显示品牌首字或内置官方标识，避免裂图。
- Copy and content: 原操作说明、草稿状态及保存发布语义保持不变。

### Primary interactions tested

1. 打开商城视觉页并点击“编辑商城视觉”，弹窗正常打开。
2. 品牌主题、商城名称、LOGO 和主题色均可见且不被裁切。
3. 真实 LOGO 成功加载；失败回退逻辑由自动化测试覆盖。
4. 取消与保存发布按钮同屏可见。
5. 页面无整体纵向溢出，手机预览保留独立滚动。

final result: passed
