#!/usr/bin/env python3
"""Actual local certificates; no network, customer certificate, or trust-store mutation."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "verify_certificate_hosts.py"
SPEC = importlib.util.spec_from_file_location("certificate_hosts", SCRIPT)
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class CertificateHostsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="mall-certificate-test-")
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        cls.exact = cls.certificate("exact", "mall.customer.test",
                                    "DNS:mall.customer.test,DNS:admin.customer.test,DNS:team.customer.test")
        cls.wildcard = cls.certificate("wildcard", "customer.test", "DNS:*.customer.test")
        cls.san_over_cn = cls.certificate("san", "mall.customer.test", "DNS:other.customer.test")

    @classmethod
    def certificate(cls, name, common_name, san):
        certificate = cls.root / (name + ".pem")
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "365",
            "-subj", "/CN=" + common_name, "-addext", "subjectAltName=" + san,
            "-keyout", str(cls.root / (name + ".key")), "-out", str(certificate),
        ], check=True, capture_output=True, timeout=30)
        return certificate

    def test_all_enabled_hosts_match(self):
        CHECKER.verify_certificate_hosts(self.exact, ["mall.customer.test", "admin.customer.test", "team.customer.test"])

    def test_public_only_does_not_require_team_host(self):
        CHECKER.verify_certificate_hosts(self.exact, ["mall.customer.test", "admin.customer.test"])

    def test_one_wrong_host_rejects_whole_set(self):
        with self.assertRaises(ValueError):
            CHECKER.verify_certificate_hosts(self.exact, ["mall.customer.test", "wrong.customer.test"])

    def test_hostname_case_is_insensitive(self):
        CHECKER.verify_certificate_hosts(self.exact, ["MALL.CUSTOMER.TEST"])

    def test_wildcard_matches_one_label(self):
        CHECKER.verify_certificate_hosts(self.wildcard, ["mall.customer.test", "admin.customer.test"])

    def test_wildcard_rejects_apex_and_multiple_labels(self):
        for host in ["customer.test", "deep.mall.customer.test", "mall.customer.test.attacker.test"]:
            with self.subTest(host=host), self.assertRaises(ValueError):
                CHECKER.verify_certificate_hosts(self.wildcard, [host])

    def test_san_overrides_common_name(self):
        with self.assertRaises(ValueError):
            CHECKER.verify_certificate_hosts(self.san_over_cn, ["mall.customer.test"])

    def test_missing_or_invalid_certificate_is_rejected(self):
        for certificate in [self.root / "missing.pem", SCRIPT]:
            with self.subTest(certificate=certificate), self.assertRaises(ValueError):
                CHECKER.verify_certificate_hosts(certificate, ["mall.customer.test"])

    def test_no_hosts_is_rejected(self):
        with self.assertRaises(ValueError):
            CHECKER.verify_certificate_hosts(self.exact, [])

    def test_zero_exit_code_without_positive_verdict_is_rejected(self):
        for output in ["", "Hostname mall.customer.test does NOT match certificate"]:
            with patch.object(CHECKER.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, output, "")):
                with self.assertRaises(ValueError):
                    CHECKER.verify_certificate_hosts(self.exact, ["mall.customer.test"])

    def test_unsupported_openssl_is_not_treated_as_success(self):
        with patch.object(CHECKER.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "unknown option")):
            with self.assertRaises(ValueError):
                CHECKER.verify_certificate_hosts(self.exact, ["mall.customer.test"])


if __name__ == "__main__":
    unittest.main()
