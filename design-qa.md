# 前端重点流程视觉验收

## 直播广场双页签与直播预告验收（2026-08-25）

- Source visual truth:
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-006e8a13-2cfc-4df4-8cb4-785280633491.png`（直播中，430 × 932）。
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-8d679716-0abf-46a0-aeb6-21af1d89e585.png`（直播预告，430 × 932）。
- Implementation screenshots:
  - `document/qa/2026-08-25-live-square-live-mobile.png`（直播中，430 × 932）。
  - `document/qa/2026-08-25-live-square-upcoming-mobile.png`（直播预告，430 × 932）。
- Side-by-side comparison:
  - `document/qa/2026-08-25-live-square-live-comparison.png`（左参考、右实现，860 × 932）。
  - `document/qa/2026-08-25-live-square-upcoming-comparison.png`（左参考、右实现，860 × 932）。
- Viewport/density: Codex应用内浏览器使用430 × 932 CSS px、devicePixelRatio 2；浏览器输出已归一为430 × 932实际像素，与参考图一比一并排比较。
- State: 直播广场总开关开启；3场直播中、6场直播预告；直播中每场关联2件真实图像商品；未登录会员点击预约时进入登录并保留回跳地址。

### Full-view comparison evidence

- 两个状态均使用与参考图一致的顶部标题和“直播中 / 直播预告”双页签；页面主体在430px手机宽度内无横向溢出。
- 直播中卡片保持左侧约46%的直播封面、右侧主播信息与上下两件商品，卡片外边距、纵向间距和信息密度与参考图接近。
- 直播预告使用两列卡片、顶部开播时间、封面、系列标题、主播和预约按钮；首屏可稳定展示两行完整卡片及第三行封面。
- 商城现有跨端导航与首页按钮继续保留，没有复制小程序专属胶囊按钮；该差异属于App、H5和小程序共用页面的既有产品外壳，不影响主体还原。

### Focused region comparison evidence

- 直播卡：参考图大直播画面约占卡片46%，实现最终调整为46%；商品缩略图提高到右侧商品行44%，标题、卖点和直播价均完整可读。
- 预告卡：手机内容区调整为左右各15px，双列间距8px，封面比例调整为1:1.14；与参考图约18px外边距、8px列间距和略高于正方形的封面节奏接近。
- 顶部区域：主题色以客户品牌色混入白色形成浅色顶栏，页签激活线由客户主题色驱动；实现不写死参考图品牌或装饰文字。
- 图像：参考图使用灰色结构占位，实现使用真实商品照片演示动态直播封面和商品缩略图；生产仍完全读取每家公司后台上传的直播与商品图片。

### Required fidelity surfaces

- Fonts and typography: 沿用商城现有Inter、苹方、微软雅黑字体栈；标题21px、页签18px，商品名称、卖点、价格和开播时间均无异常截断或挤压。
- Spacing and layout rhythm: 430px手机视口下使用15px主体外边距、8～10px卡片间距；直播卡左右46/54，预告两列等宽，圆角与现有商城卡片统一。
- Colors and visual tokens: 顶栏、激活线、已预约状态读取客户主题色；直播中使用高对比直播红，价格使用商城价格色，文本对比度清晰。
- Image quality and asset fidelity: 本地视觉数据使用线上公开商城真实商品图片，不以灰色占位、手绘图形或CSS图案冒充业务图片；图标沿用商城既有图标库。
- Copy and content: 保留“直播广场、直播中、直播预告、预约、已预约、官方直播间、直播价”等真实业务文案；开播时间由服务端计划时间计算，不写死参考图时间。

### Findings and comparison history

- [P2，已修复] 初版内容区仅8px外边距、直播封面占48%，预告卡较参考图偏宽偏矮；最终调整为15px外边距、直播封面46%和预告封面1:1.14后重新截图并排比较。
- [P2，已修复] 初版页签额外显示场次数量，信息层级比参考图更拥挤；已移除数量徽标，保留清晰的两个状态页签。
- Post-fix并排证据为上述两张comparison图片。最终未发现仍需处理的P0、P1或P2问题；顶部小程序胶囊与商城底部导航差异属于跨端产品外壳的预期差异。

### Primary interactions tested

1. “直播中”和“直播预告”页签可相互切换，DOM选中状态与可见内容同步。
2. 直播封面、主播区和两件商品分别保持正确详情链接。
3. 未登录点击“预约”进入登录页，回跳地址完整保留`/live?tab=upcoming`；后端专项测试覆盖登录会员预约、重复预约、取消及非预告状态拒绝。
4. 手机视口控制台错误和警告均为0。

final result: passed

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

## 直播广场与新品速递横排验收（2026-08-25）

- Source visual truth: `document/qa/2026-08-25-live-new-horizontal-reference.png`（718 × 428）。
- Implementation screenshot: `document/qa/2026-08-25-live-new-horizontal-mobile.png`（360 × 222，手机双卡区域密度归一化截图）。
- Side-by-side comparison: `document/qa/2026-08-25-live-new-horizontal-comparison.png`（左侧参考、右侧实现）。
- Viewport/density/state: Codex 应用内浏览器设置 390 × 844，实际可视宽度 375，devicePixelRatio 2；本地只读代理使用线上公开商城商品资料，并仅在本地响应中补入一场直播和一个新品用于显式展示，未写入生产数据。
- Density normalization: 参考图按 360px 宽等比归一为 360 × 215；实现区域按浏览器 2 倍密度归一为 360 × 222。比较不包含浏览器外壳、底部导航或后续商品区。

### Full-view comparison evidence

- “直播广场”和“新品速递”已合并为同一行的左右双卡片，每列独立保留标题、“全部”入口、真实封面、状态/新品标识、名称和热度/首发价。
- 两项继续使用各自的运营开关和数据门禁；同时可见时按后台模块顺序决定左右位置，其中一项关闭或没有内容时，剩余项自动占满整行，不出现半行空洞。
- 后台“客户手机版预览”同步使用相同横排结构，避免装修预览与真实商城不一致。

### Focused region comparison evidence

- 布局：参考图与实现均为一行两列、两列同宽、标题和“全部”在卡片上方，手机端无横向溢出。
- 比例：初版沿用旧 4:5 竖卡，双列后区域高 248px，较参考图偏高；已将手机卡片调整为 9:10，最终区域高约 223px，与归一化参考图 215px 接近。
- 产品边界：参考图中的符号占位被真实直播状态、NEW 标识和真实商品图片替代；这是对商城既有业务信息的保留，不复制参考图中的虚构热度或价格。

### Required fidelity surfaces

- Fonts and typography: 沿用商城既有中文字体、标题权重和价格层级；两列标题及“全部”不换行、不裁切。
- Spacing and layout rhythm: 手机内容宽度扣除 8px 双侧边距，列间距 10px；两张卡等宽等高，9:10 比例和 15px 圆角接近参考图节奏。
- Colors and visual tokens: 继续使用商城主题色、直播红色状态和新品紫色标识，不引入与现有品牌冲突的新色板。
- Image quality and asset fidelity: 实现截图使用真实商城商品图作为本地视觉数据，不使用灰色占位块、手绘图标或伪造内容冒充交付效果。
- Copy and content: 保留“直播广场”“新品速递”“全部”、直播标题/主播/热度和商品标题/首发价；没有把参考图中的示例价格、上线日期或热度写入产品。

### Findings and comparison history

- [P2，已修复] 初版横排继续使用旧 4:5 卡片，手机双卡整体偏高；调整为 9:10 后重新截图比较，区域高度与参考图接近，首屏信息密度更合理。
- 最终并排比较未发现仍需处理的 P0、P1 或 P2 问题；参考图与实现的内容差异属于真实业务数据和既有品牌体系的预期差异。

### Primary interactions tested

1. 左侧“全部”进入 `/live`，右侧“全部”进入 `/new-arrivals`。
2. 两项同时开放时保持一行两列；单项状态由自动化测试覆盖为独立展示并占满整行。
3. 商城 85/85、后台 123/123 自动化通过；公开商城、三合一 H5 和管理后台生产构建通过。
4. 手机预览浏览器控制台错误和警告均为 0。

final result: passed

## 直播广场与新品速递验收（2026-08-23）

- Source visual truth: `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-abfc96e0-bea4-4401-839f-712a68af641f.png`（430 × 932）。
- Implementation screenshots:
  - `document/qa/2026-08-23-live-new-home-viewport.png`（415 × 869，首页首屏；浏览器可用内容宽度扣除滚动条）。
  - `document/qa/2026-08-23-live-square-mobile.png`（430 × 900）。
  - `document/qa/2026-08-23-live-room-mobile.png`（430 × 900）。
  - `document/qa/2026-08-23-new-arrivals-mobile.png`（430 × 900）。
- Viewport/density/state: 430 × 932 CSS px，devicePixelRatio 2；本地隔离 API 返回一场真实状态直播和两个真实商品对象，未使用静态占位卡冒充接口数据。

### Full-view comparison evidence

- 参考图要求首页在分类之后并排展示“直播广场”和“新品速递”。实现保持相同信息架构，并使用现有灵启商城圆角、红色主题和商品卡体系，不复制参考图的品牌或底部“生活号/消息”业务。
- 首页两个入口在一个手机首屏内可见；直播卡展示状态、标题、主播和热度，新品卡展示商品、首发价和新品标识。无任何公开直播或新品数据时，整组模块自动隐藏。
- 直播广场、直播详情和新品列表均具备独立路由、返回首页、加载、失败和空状态；底部商城导航保持现有产品边界。

### Focused region comparison evidence

- 直播：首页以真实封面为主视觉，“直播中”使用高对比红色状态；详情页把公开观看入口与关联商品分开，预告状态不会暴露观看地址。
- 新品：两列卡片在 430px 下无横向溢出，商品名、说明、价格和销量层级清楚；新品以首次正式上架时间归集，后续编辑不会重复计入。
- 可访问性：三个新页面均有语义标题、图片替代文本、返回与首页按钮名称，直播/商品卡可由链接直接操作。

### Findings and iterations

- 初始实现已满足参考图的双入口结构；产品复核后增加独立直播详情和新品全量页，避免首页卡片成为无后续流程的装饰入口。
- 服务端补充显式客户范围、公开字段最小化、直播状态约束、在售商品校验和乐观版本控制；云直播推流密钥不进入业务表。
- 浏览器验收未发现 P0、P1 或 P2 视觉问题；首页、直播广场、直播详情和新品列表无白屏、无横向溢出、无阻断控制台错误。

passed

## 活动单列版与直播总开关验收（2026-08-23）

- Source visual truth: `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-51b3c614-d0be-43cf-8a50-67cd3fd9972f.png`（714 × 1438）。
- Implementation evidence: `document/qa/2026-08-23-campaign-feed-home-mobile.png`（415 × 869 浏览器内容像素）。
- Viewport/density/state: 430 × 932 CSS px，devicePixelRatio 2；后台选择 `campaign-feed`，本地隔离接口返回真实活动状态、起止时间、活动价和商品数据。

### Full-view comparison evidence

- 实现复用参考图的信息结构：淡色品牌与搜索区、横向分类、大图单列商品卡、橙色活动状态带、商品名称、销量、价格和绿色购买按钮；继续使用灵启商城自己的品牌、颜色和导航，不复制参考品牌“数商臻品”。
- 手机端为单列，1280 × 800 电脑端自动为双列；桌面文档宽度小于视口宽度，无横向溢出。版型切换保留运营现有的轮播图开关，不会擅自删除或隐藏已配置内容。
- 关闭直播广场后，首页直播列、公开直播列表和直接详情均由服务端共同关闭，后台直播间资料继续保留。

### Focused region comparison evidence

- 活动条：仅真实 `ACTIVE`/`UPCOMING` 秒杀活动显示，倒计时按接口起止时间每秒更新；活动按钮进入 `/flash-sale?activityId=...`，未登录时正确进入登录回跳。
- 普通商品：无真实活动时不显示倒计时或虚假活动价，保留标准加入购物车流程。
- 分类与商品密度：430px 下分类横向滚动，商品图片比例约 2.05:1，标题、销量、价格和按钮在一张卡内完整呈现。

### Findings and iterations

- 初始实现同时显示业务快捷入口和商品区，造成首屏信息重复；活动商品图片也占用过高，首屏商品露出不足。
- 修复后，活动单列版隐藏重复的业务快捷入口、缩短商品图比例并在无搜索筛选时隐藏重复商品标题；轮播图继续尊重运营已有开关，使首屏更接近参考图的信息密度且不破坏现有装修。
- Post-fix evidence: `document/qa/2026-08-23-campaign-feed-home-mobile.png`。未发现 P0、P1 或 P2 视觉问题。

### Required fidelity surfaces

- Fonts and typography: 使用商城现有中文字体层级，活动带与价格通过粗细和颜色建立主次，未出现裁切或异常换行。
- Spacing and layout rhythm: 品牌区、分类条和商品卡使用紧凑的 8～18px 节奏，连续浏览时卡片边界清晰。
- Colors and visual tokens: 主题色由后台配置驱动；橙色活动带和绿色购买按钮用于活动语义，不影响其他模板。
- Image quality and asset fidelity: 使用接口商品图和商城品牌资源，未使用灰色占位块冒充交付效果。
- Copy and content: 活动文案由真实状态生成；销量、价格和按钮含义与商城既有交易流程一致。

### Primary interactions tested

1. 手机端活动商品的“去抢购”进入对应秒杀活动，未登录时保留完整回跳地址。
2. 普通商品继续显示加入购物车按钮，不被活动模板改变业务类型。
3. 电脑端双列与手机端单列均无横向溢出。
4. 新开浏览器页面无阻断控制台错误。
5. 后台可在四种版型间切换，并独立控制直播广场公开状态。

final result: passed
