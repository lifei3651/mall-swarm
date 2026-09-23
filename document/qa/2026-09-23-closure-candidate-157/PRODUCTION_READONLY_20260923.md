# 1.0.157 服务器发布前只读复核（非发布回执）

核对时间：2026-09-23 10:58–10:59（Asia/Shanghai）

## 已直接核对

- 在干净且与远程同步的当前分支，对 `target/releases/lingqi-mall-1.0.157-ef9da4bd.tar.gz` 和已提交的 `artifact-retention.json` 执行完整候选准入；统一包、四个静态内包、小程序不可变源码、逐文件哈希、41 条迁移集合、Nginx 模板及 P0-10 私有附件回读验签均返回 `release-readiness-ok`。
- 正式主机只读准入返回 `release-readiness-ok`：主机身份为脚本固定的商城主机，Nginx、MySQL、Redis、商城后端四项服务为 active，后端 health 为 UP，正式 Nginx 配置哈希与仓库模板一致，公开商城版本清单仍属于拆分版。首次受限网络环境无法解析主机；在获准的只读网络环境重试后通过，未作为产品故障记录。
- 独立读取 `https://lingqimall.com/version.json`、`https://www.lingqimall.com/version.json` 和 `https://lingqimall.com/admin/version.json`：三者均为 `1.0.154 / 20260921-closure-1.0.154 / app-h5-split`。本轮未把网页版本推断为后端版本。

## 尚未执行或尚未证明

- 没有备份、创建隔离数据库、执行 40→41 迁移、部署后端或静态站、修改正式库、上传微信开发版、设置体验版、提审或正式发布。
- 本轮只读脚本未逐条读取正式数据库迁移历史、未取得受影响角色权限和真机交易 R；上述结果不能替代完整 S/W/R 回执。
- 当前下一门槛仍是明确的验证版发布授权及其后的备份、隔离迁移演练、受控 S 和独立复核。P0-10 已关闭不等于其余 P0 清零。
