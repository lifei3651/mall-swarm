# 基座私有交付：仅公开商城模式（2026-09-26）

范围：客户独立项目可选择只交付公开商城、管理后台与同一后端；原公开商城＋团队 H5 拆分模式保持默认。当前基座测试站 `1.0.167` 未切换，本文不代表新候选包或真实客户部署。

## 本轮完成的源码/自动检查

- `deploy.sh build --without-team-h5` 只执行公开商城构建和后台/后端构建；商城完整自动测试仍执行。待交付 `html` 不包含 `team` 或 `integrated`。
- `deploy.sh prepare --without-team-h5` 生成空 `TEAM_DOMAIN`、仅公开与后台来源的 CORS、受保护的独立 Nginx 模板选择；与 `--team-domain` 不能同时使用。
- 独立 Nginx 模板只含公开商城与后台站点，未知 Host 拒绝；预检按选中模式核验证书、静态目录、版本与模板，拒绝残留团队资源或配置错配。实际展开的 Compose 配置还核对域名和模板挂载，避免调用环境变量覆盖受保护 `.env`。后置验收也按模式检查运行容器资源与站点身份。
- 隔离临时目录执行 `document/private-deploy/tests/deployment_security_test.sh`：默认拆分与仅公开模式均通过预检；无云安全组确认、团队资源残留、模板错配、模式参数冲突等反例正确拒绝；两种模式的 Compose 展开挂载校验通过。
- `python3 -m unittest document/private-deploy/tests/test_validate_compose.py` 10 项通过；四份变更脚本 `sh -n` 与 `git diff --check` 通过。

## 没有完成、不得追认

- Docker CLI 可展开 Compose，但本机 Docker daemon 未运行；未真正启动 Nginx/后端，也未执行完整 Maven/npm 生产构建、部署后检查或模式切换回滚演练。
- 未生成新版本号、固定候选包或私有留档；未部署服务器、上传微信或做真机/真实交易验收。
- 不部署团队站只解决前端站点交付组合，不证明奖金、余额、多商户等后端模块已可独立裁剪；P1-14 仍待客户隔离环境运行复核，P0 原有门禁不变。
