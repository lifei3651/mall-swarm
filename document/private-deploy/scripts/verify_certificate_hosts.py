#!/usr/bin/env python3
"""Offline hostname check delegated to OpenSSL, independent of Python's removed ssl.match_hostname."""

import os
import subprocess
import sys


def verify_certificate_hosts(certificate, hostnames):
    if not hostnames:
        raise ValueError("未提供需要验证的域名")
    for hostname in hostnames:
        result = subprocess.run(
            ["openssl", "x509", "-in", str(certificate), "-noout", "-checkhost", hostname],
            capture_output=True, text=True, timeout=10,
            env={**os.environ, "LC_ALL": "C"},
        )
        # Some OpenSSL versions return zero even for a hostname mismatch. Require the positive verdict.
        if result.returncode != 0 or result.stdout.strip() != "Hostname {} does match certificate".format(hostname):
            raise ValueError("证书未覆盖域名 {}，或 OpenSSL 不支持 -checkhost（需 1.1.1+ / 3.x）".format(hostname))


if __name__ == "__main__":
    try:
        if len(sys.argv) < 3:
            raise ValueError("用法：verify_certificate_hosts.py CERT HOST [HOST ...]")
        verify_certificate_hosts(sys.argv[1], sys.argv[2:])
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print("TLS 域名检查失败：{}".format(error), file=sys.stderr)
        sys.exit(1)
