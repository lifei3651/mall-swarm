#!/usr/bin/env bash

# 灵启商城正式环境唯一目标。发布、验收和证书检查必须从这里读取，禁止临时猜测 IP。
readonly LINGQIMALL_PRODUCTION_SSH_HOST="lingqimall.com"
readonly LINGQIMALL_PRODUCTION_HOSTNAME="VM-4-6-rockylinux"
readonly LINGQIMALL_PRODUCTION_NGINX_CONFIG="/etc/nginx/conf.d/lingqimall.conf"
# 该实例承载其他项目，不属于灵启商城。商城脚本不得连接、发布或据此判断线上状态。
readonly LINGQIMALL_FORBIDDEN_DEPLOY_HOSTS=("121.40.242.129")
readonly LINGQIMALL_NON_MALL_INSTANCE_IDS=("i-bp1fge2q8pheifo6dq1x")
