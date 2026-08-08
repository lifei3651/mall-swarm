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

final result: blocked（登录图形验证码阻断了登录后视觉验收；代码测试与生产构建均已通过）
