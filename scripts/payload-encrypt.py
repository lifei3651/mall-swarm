#!/usr/bin/env python3
"""Encrypt sensitive JSON fields exactly like the Lingqi Mall web clients.

Reads a JSON object from stdin and writes a JSON envelope containing the
encrypted request body and the two one-time challenge headers. When the body
has no sensitive values, it is returned unchanged without requesting a key.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import time
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


SENSITIVE_FIELDS = {
    "password",
    "currentpassword",
    "newpassword",
    "oldpassword",
    "loginpassword",
    "paymentpassword",
    "adminpassword",
    "confirmpassword",
    "smscode",
    "currentphonesmscode",
    "newphonesmscode",
    "captchacode",
    "appsecret",
    "callbacktoken",
    "code",
}


def sensitive_entries(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if str(key).lower() in SENSITIVE_FIELDS and isinstance(child, str) and child:
                yield value, key, child
            else:
                yield from sensitive_entries(child)
    elif isinstance(value, list):
        for child in value:
            yield from sensitive_entries(child)


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: payload-encrypt.py API_BASE")
    api_base = sys.argv[1].rstrip("/")
    body = json.load(sys.stdin)
    entries = list(sensitive_entries(body))
    if not entries:
        json.dump({"body": body, "challengeId": "", "encryptedKey": ""}, sys.stdout, ensure_ascii=False)
        return 0

    request = urllib.request.Request(
        f"{api_base}/security/payload-encryption/key?_={int(time.time() * 1000)}",
        headers={"Cache-Control": "no-store"},
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        challenge_response = json.load(response)
    if int(challenge_response.get("code", 0)) != 200:
        raise RuntimeError(challenge_response.get("message") or "cannot obtain payload encryption challenge")
    challenge = challenge_response["data"]
    challenge_id = challenge["challengeId"]
    public_key = serialization.load_der_public_key(base64.b64decode(challenge["publicKey"]))
    aes_key = AESGCM.generate_key(bit_length=256)
    encrypted_key = public_key.encrypt(
        aes_key,
        padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    aes = AESGCM(aes_key)
    for container, field_name, plain_text in entries:
        iv = os.urandom(12)
        aad = f"{challenge_id}:{str(field_name).lower()}".encode("utf-8")
        cipher_text = aes.encrypt(iv, plain_text.encode("utf-8"), aad)
        container[field_name] = "enc:v1:{}:{}".format(
            base64.b64encode(iv).decode("ascii"),
            base64.b64encode(cipher_text).decode("ascii"),
        )
    json.dump(
        {
            "body": body,
            "challengeId": challenge_id,
            "encryptedKey": base64.b64encode(encrypted_key).decode("ascii"),
        },
        sys.stdout,
        ensure_ascii=False,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
