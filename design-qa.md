# 登录与注册页视觉验收

- Source visual truth:
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-73d97036-a862-4370-b0a1-ad14bd2be3a1.jpg`
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-fe48d2db-5a37-4b91-ab5d-5ef584098680.png`
- `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-02ab7448-4b1b-4318-8920-62e3d9efc9bd.jpg`
- Implementation screenshot: `document/qa/20260808-register-account-validation-mobile.jpg`
- Viewport: 390 × 844 mobile CSS viewport.
- State: 未登录访客；注册表单默认状态与登录空表单校验状态。

## 对比结果

- 登录必填错误已从表单底部移到对应输入框下方，不再与触发字段脱节。
- 字段错误 2 秒自动消失；接口成功或失败提示使用顶部短时浮层，1.8 秒自动消失。
- 切换登录方式、登录/注册页面或离开当前路由时，已有提示立即清空。
- 注册页改为紧凑双列结构：手机号/登录账号、登录密码并排；邀请码、短信验证码和协议保持整行，阅读顺序清晰；昵称已移出注册流程。
- 登录与注册页隐藏商城页脚和底部导航，390 × 844 视口内可完整看到注册表单、协议、提交按钮和返回登录入口。
- 登录账号仅接受英文字母、数字和下划线，4～20 位且必须以英文字母开头；中文、空格和特殊符号在输入阶段直接过滤，结构错误紧贴账号输入框提示。
- “手机号已注册”等接口错误按内容回填到手机号、登录账号、邀请码、验证码或密码输入框，不再堆到表单底部。

## 交互验收

1. 空表单点击“登录”，仅手机号/用户名字段下方出现对应提示。
2. 等待 2 秒，字段提示自动消失。
3. 切换到注册页，登录提示不残留。
4. 注册页在 390 × 844 视口无横向溢出，主要操作无需页面滚动即可完成。
5. 输入 `蜗牛 user@123` 后实际保留 `user123`；输入 `1234` 离开字段后显示“登录账号必须以英文字母开头”，2 秒后自动消失。
6. 页面实测 `innerHeight=844`、`scrollHeight=844`，无额外纵向滚动。

## Findings

未发现影响本次登录提示和注册页紧凑布局的 P0、P1 或 P2 问题。

final result: passed
