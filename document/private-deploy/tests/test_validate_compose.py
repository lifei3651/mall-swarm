import copy
import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "scripts" / "validate_compose.py"
SPEC = importlib.util.spec_from_file_location("validate_compose", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def safe_config():
    return {
        "services": {
            "mysql": {
                "image": "mysql:8.4.10",
                "environment": {"MYSQL_ROOT_PASSWORD": "secret", "MYSQL_PASSWORD": "secret"},
                "networks": {"data": None},
                "read_only": True,
                "security_opt": ["no-new-privileges:true"],
                "cpus": 2.0,
                "mem_limit": 2147483648,
                "pids_limit": 256,
            },
            "redis": {
                "image": "redis:7.2.15-alpine",
                "environment": {"REDIS_PASSWORD": "secret"},
                "command": ["sh", "-c", 'redis-server --rename-command KEYS "" --rename-command FLUSHALL "" --rename-command FLUSHDB "" --rename-command CONFIG "" --rename-command SHUTDOWN "" --rename-command DEBUG "" --rename-command MODULE ""'],
                "networks": {"data": None},
                "read_only": True,
                "security_opt": ["no-new-privileges:true"],
                "cpus": 1.0,
                "mem_limit": 536870912,
                "pids_limit": 128,
            },
            "mall-distribution": {
                "environment": {
                    "SPRING_PROFILES_ACTIVE": "prod",
                    "SPRING_CLOUD_NACOS_DISCOVERY_ENABLED": "false",
                    "SPRING_CLOUD_NACOS_CONFIG_ENABLED": "false",
                    "SHOP_PAYMENT_SIMULATION_ENABLED": "false",
                    "SMS_EXPOSE_CODE": "false",
                    "SMS_TEST_CODE": "",
                    "DB_PASSWORD": "secret",
                    "REDIS_PASSWORD": "secret",
                    "DATA_ENCRYPTION_KEY": "secret",
                    "DATA_ENCRYPTION_WRITE_ENABLED": "true",
                    "TENCENT_FACEID_SECRET_ID": "secret-id",
                    "TENCENT_FACEID_SECRET_KEY": "secret-key",
                    "DB_SSL_MODE": "REQUIRED",
                    "ALIPAY_PRIVATE_KEY": "",
                    "SMS_ALIYUN_ACCESS_KEY_SECRET": "",
                    "NOTIFICATION_SMS_ALIYUN_ACCESS_KEY_SECRET": "",
                    "NOTIFICATION_SMS_ALIYUN_RECEIPT_SECRET": "",
                },
                "networks": {"edge": None, "data": None},
                "read_only": True,
                "security_opt": ["no-new-privileges:true"],
                "cpus": 2.0,
                "mem_limit": 2147483648,
                "pids_limit": 256,
            },
            "nginx": {
                "image": "nginx:1.28.3-alpine",
                "environment": {},
                "networks": {"edge": None},
                "security_opt": ["no-new-privileges:true"],
                "read_only": True,
                "cpus": 1.0,
                "mem_limit": 268435456,
                "pids_limit": 128,
                "ports": [
                    {"target": 80, "published": "80", "host_ip": "0.0.0.0"},
                    {"target": 443, "published": "443", "host_ip": "0.0.0.0"},
                ],
            },
        },
        "networks": {"edge": {}, "data": {"internal": True}},
    }


class ComposeSecurityValidationTest(unittest.TestCase):
    def test_accepts_current_security_boundary(self):
        self.assertEqual([], MODULE.validate(safe_config()))

    def test_accepts_compose_equals_security_option(self):
        config = safe_config()
        config["services"]["mall-distribution"]["security_opt"] = [
            "no-new-privileges=true"
        ]
        config["services"]["nginx"]["security_opt"] = [
            "no-new-privileges=true"
        ]
        self.assertEqual([], MODULE.validate(config))

    def test_rejects_writable_container_missing_limits_and_unencrypted_database(self):
        config = safe_config()
        config["services"]["mysql"]["read_only"] = False
        config["services"]["redis"].pop("mem_limit")
        config["services"]["mall-distribution"]["environment"]["DB_SSL_MODE"] = "DISABLED"
        errors = MODULE.validate(config)
        self.assertTrue(any("mysql 根文件系统必须只读" in error for error in errors))
        self.assertTrue(any("redis 必须设置有效的内存资源上限" in error for error in errors))
        self.assertTrue(any("数据库连接必须启用 TLS" in error for error in errors))

    def test_rejects_public_redis_port(self):
        config = safe_config()
        config["services"]["redis"]["ports"] = [
            {"target": 6379, "published": "6379", "host_ip": "0.0.0.0"}
        ]
        self.assertTrue(any("端口" in error for error in MODULE.validate(config)))

    def test_rejects_redis_without_dangerous_command_lockdown(self):
        config = safe_config()
        config["services"]["redis"]["command"] = ["redis-server", "--requirepass", "secret"]
        self.assertTrue(any("Redis必须禁用危险命令" in error for error in MODULE.validate(config)))

    def test_rejects_floating_image_tag(self):
        config = safe_config()
        config["services"]["nginx"]["image"] = "nginx:latest"
        self.assertTrue(any("镜像版本" in error for error in MODULE.validate(config)))

    def test_rejects_secret_leak_to_nginx(self):
        config = safe_config()
        config["services"]["nginx"]["environment"]["ALIPAY_PRIVATE_KEY"] = "leaked"
        config["services"]["nginx"]["environment"]["TENCENT_FACEID_SECRET_KEY"] = "leaked"
        self.assertTrue(any("ALIPAY_PRIVATE_KEY" in error for error in MODULE.validate(config)))
        self.assertTrue(any("TENCENT_FACEID_SECRET_KEY" in error for error in MODULE.validate(config)))

    def test_rejects_simulation_and_test_sms(self):
        config = safe_config()
        app = config["services"]["mall-distribution"]["environment"]
        app["SHOP_PAYMENT_SIMULATION_ENABLED"] = "true"
        app["SMS_EXPOSE_CODE"] = "true"
        errors = MODULE.validate(config)
        self.assertTrue(any("模拟支付" in error for error in errors))
        self.assertTrue(any("固定短信" in error for error in errors))

    def test_rejects_host_network_and_docker_socket(self):
        config = copy.deepcopy(safe_config())
        config["services"]["nginx"]["network_mode"] = "host"
        config["services"]["nginx"]["volumes"] = ["/var/run/docker.sock:/var/run/docker.sock"]
        errors = MODULE.validate(config)
        self.assertTrue(any("host 网络" in error for error in errors))
        self.assertTrue(any("Docker socket" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
