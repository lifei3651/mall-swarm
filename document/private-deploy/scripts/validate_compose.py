#!/usr/bin/env python3
"""Validate rendered Docker Compose JSON without printing secret values."""

import json
import sys
from pathlib import Path


REQUIRED_SERVICES = {"mysql", "redis", "mall-distribution", "nginx"}


def environment_map(service):
    value = service.get("environment") or {}
    if isinstance(value, dict):
        return value
    result = {}
    for item in value:
        key, _, val = str(item).partition("=")
        result[key] = val
    return result


def network_names(service):
    value = service.get("networks") or {}
    return set(value if isinstance(value, list) else value.keys())


def validate(config):
    errors = []
    services = config.get("services") or {}
    if set(services) != REQUIRED_SERVICES:
        errors.append("服务集合必须严格等于当前商城基座，禁止遗留或临时增加容器")
    expected_images = {
        "mysql": "mysql:8.4.10",
        "redis": "redis:7.2.15-alpine",
        "nginx": "nginx:1.28.3-alpine",
    }
    for name, image in expected_images.items():
        if services.get(name, {}).get("image") != image:
            errors.append(f"{name} 必须使用已测试的明确镜像版本，禁止 latest 或漂移标签")

    networks = config.get("networks") or {}
    data_network = networks.get("data") or next(
        (value for name, value in networks.items() if name.endswith("_data")), {}
    )
    if not data_network.get("internal"):
        errors.append("数据库内部网络必须设置 internal=true")

    expected_networks = {
        "mysql": {"data"},
        "redis": {"data"},
        "mall-distribution": {"edge", "data"},
        "nginx": {"edge"},
    }
    for name, expected in expected_networks.items():
        actual = network_names(services.get(name, {}))
        normalized = {part.rsplit("_", 1)[-1] for part in actual}
        if normalized != expected:
            errors.append(f"{name} 网络边界不正确")

    for name, service in services.items():
        if service.get("privileged") or service.get("network_mode") == "host":
            errors.append(f"{name} 禁止特权模式或 host 网络")
        if service.get("pid") == "host" or service.get("ipc") == "host":
            errors.append(f"{name} 禁止共享宿主机命名空间")
        if service.get("devices") or service.get("cap_add"):
            errors.append(f"{name} 禁止额外设备或 Linux capabilities")
        for volume in service.get("volumes") or []:
            source = volume if isinstance(volume, str) else str(volume.get("source", ""))
            if "docker.sock" in source or source.endswith("/.env"):
                errors.append(f"{name} 禁止挂载 Docker socket 或客户密钥文件")

        for port in service.get("ports") or []:
            if isinstance(port, str):
                errors.append(f"{name} 的端口必须由 Compose 展开为结构化配置")
                continue
            target = int(port.get("target", 0))
            published = int(str(port.get("published", "0")))
            if name == "nginx" and (target, published) in {(80, 80), (443, 443)}:
                continue
            errors.append(f"{name} 存在不允许的宿主机端口映射")

    sensitive_owners = {
        "MYSQL_ROOT_PASSWORD": {"mysql"},
        "DB_PASSWORD": {"mall-distribution"},
        "REDIS_PASSWORD": {"redis", "mall-distribution"},
        "SA_TOKEN_JWT_KEY": {"mall-distribution"},
        "DATA_ENCRYPTION_KEY": {"mall-distribution"},
        "DATA_ENCRYPTION_WRITE_ENABLED": {"mall-distribution"},
        "ALIPAY_PRIVATE_KEY": {"mall-distribution"},
        "SMS_ALIYUN_ACCESS_KEY_SECRET": {"mall-distribution"},
    }
    for key, owners in sensitive_owners.items():
        actual = {name for name, service in services.items() if key in environment_map(service)}
        if actual != owners:
            errors.append(f"{key} 的容器最小权限边界不正确")

    app_env = environment_map(services.get("mall-distribution", {}))
    if not services.get("mall-distribution", {}).get("read_only"):
        errors.append("商城后端根文件系统必须只读")
    for name in ("mall-distribution", "nginx"):
        options = {
            str(option).replace("=", ":", 1)
            for option in services.get(name, {}).get("security_opt") or []
        }
        if "no-new-privileges:true" not in options:
            errors.append(f"{name} 必须禁止获取新权限")
    if app_env.get("SPRING_PROFILES_ACTIVE") != "prod":
        errors.append("商城后端必须强制使用 prod 环境")
    if str(app_env.get("SPRING_CLOUD_NACOS_DISCOVERY_ENABLED", "")).lower() != "false" \
            or str(app_env.get("SPRING_CLOUD_NACOS_CONFIG_ENABLED", "")).lower() != "false":
        errors.append("当前单体商城基座必须关闭不必要的 Nacos 客户端")
    if str(app_env.get("SHOP_PAYMENT_SIMULATION_ENABLED", "")).lower() != "false":
        errors.append("私有部署必须强制关闭模拟支付")
    if str(app_env.get("SMS_EXPOSE_CODE", "")).lower() != "false" or app_env.get("SMS_TEST_CODE"):
        errors.append("私有部署必须强制关闭固定短信验证码")
    redis_command = services.get("redis", {}).get("command") or []
    redis_command_text = " ".join(redis_command) if isinstance(redis_command, list) else str(redis_command)
    for dangerous_command in ("KEYS", "FLUSHALL", "FLUSHDB", "CONFIG", "SHUTDOWN", "DEBUG", "MODULE"):
        if f'rename-command {dangerous_command} ""' not in redis_command_text:
            errors.append(f"Redis必须禁用危险命令 {dangerous_command}")
    return errors


def main():
    if len(sys.argv) != 2:
        print("usage: validate_compose.py <compose-config.json>", file=sys.stderr)
        return 2
    try:
        config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"无法读取 Compose 配置：{exc}", file=sys.stderr)
        return 2
    errors = validate(config)
    for error in errors:
        print(f"安全预检失败：{error}", file=sys.stderr)
    if errors:
        return 1
    print("Compose 端口、网络、生产模式和密钥最小权限检查通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
