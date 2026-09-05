# Native mini-program payload encryption

This directory vendors a restricted build of **node-forge 1.4.0**, used under its
BSD-3-Clause license (see `LICENSE`). `SOURCE.json` records the official npm
archive URL, pinned SHA-512 integrity, build-tool version and generated SHA-256.
There are no runtime npm dependencies and no application private keys here.

## Reproduction

From the repository root:

```sh
node mall-mini-program/scripts/build-payload-crypto.mjs
node --test mall-mini-program/tests/payload-encryption.test.mjs
node mall-mini-program/scripts/check-payload-interop.mjs
```

The last command requires the existing backend
`PayloadEncryptionServiceImplTest` to have run first, providing its compiled
classes and Surefire classpath. Its bridge runs the actual production Java
encryption service with an in-memory Redis one-use store. It makes no HTTP/Redis
connections, and keeps test keys and values in process memory only.

The generator downloads the pinned archive from the official npm registry,
verifies its actual SHA-512 before extraction, validates archive paths and uses
an isolated temporary build directory. Build dependencies do not modify the
application package or lockfile. `scripts/` and `tests/` are development-only
inputs; only the generated vendor module is required by the mini-program.

## Protocol and restrictions

- Primitives: upstream RSA-OAEP with SHA-256 **and MGF1 SHA-256**, AES-256-GCM with
  a 128-bit authentication tag. RSA public keys must be 2048–4096 bits with
  exponent 65537. The adapter validates the server algorithm, key, challenge
  format and expiration before encryption.
- The only entropy source is the official asynchronous `wx.getRandomValues`
  (base library 2.15.0+), returning an ArrayBuffer of the exact requested length.
  The forge default PRNG and all upstream `Math.random` calls are replaced with
  throwing stubs. There is no weak or implicit random fallback.
- Each sensitive request gets a fresh server challenge, AES key and OAEP seed;
  each nonempty sensitive field gets its own 12-byte IV and lowercased field-name
  AAD, bound to the challenge. The exact backend-sensitive field list is tested.
- Sensitive data in query parameters is rejected, not encrypted into a URL.
  Invalid challenges, missing entropy, session changes and encryption errors
  stop submission; no plaintext fallback is allowed. Ordinary public requests
  do not fetch challenges or require cryptographic APIs.
- Request data is snapshotted before async operations. Session identity is
  captured at request entry and checked before both transmissions and when
  responses arrive. Encryption headers cannot override caller authentication.
- Ciphertext envelopes supplement HTTPS; they do not replace TLS, server
  validation, authorization, idempotency or payment verification. JavaScript
  does not guarantee erasure of immutable strings from memory, so neither this
  adapter nor its callers should log or persist sensitive inputs.

## Primary references

- [Upstream node-forge](https://github.com/digitalbazaar/forge)
- [Upstream changelog](https://github.com/digitalbazaar/forge/blob/main/CHANGELOG.md)
- [wx.getRandomValues](https://developers.weixin.qq.com/miniprogram/dev/api/device/crypto/wx.getRandomValues.html)
- [Official WeChat API typings](https://github.com/wechat-miniprogram/api-typings/blob/master/types/wx/lib.wx.api.d.ts)

The VM tests deliberately supply no `window`, `self`, `Buffer`, Node crypto or
browser WebCrypto globals to the actual generated bundle. An independent Node
crypto decoder verifies every protected field and rejects changed tags/AAD;
the Java interoperability check additionally verifies production decryption and
challenge replay rejection. These local checks do not claim real-account or
real-payment acceptance.
