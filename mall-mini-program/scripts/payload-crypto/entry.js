// Application-facing surface of node-forge. Cryptographic primitives remain upstream implementations.
const forge = require('node-forge/lib/forge')
require('node-forge/lib/asn1')
require('node-forge/lib/rsa')
require('node-forge/lib/aes')
require('node-forge/lib/sha256')

function binary(bytes) {
  let output = ''
  for (let index = 0; index < bytes.length; index++) output += String.fromCharCode(bytes[index])
  return output
}

function readPublicKey(encoded) {
  if (typeof encoded !== 'string' || encoded.length > 2048 || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(encoded)) {
    throw new Error('Invalid payload public key')
  }
  const der = forge.util.decode64(encoded)
  if (!der || forge.util.encode64(der) !== encoded) throw new Error('Invalid payload public key')
  const key = forge.pki.publicKeyFromAsn1(forge.asn1.fromDer(der, { strict: true, parseAllBytes: true }))
  if (key.n.bitLength() < 2048 || key.n.bitLength() > 4096 || key.e.toString(10) !== '65537') throw new Error('Invalid payload RSA parameters')
  return key
}

module.exports = {
  readPublicKey,
  wrapKey(publicKey, rawKey, oaepSeed) {
    if (rawKey.length !== 32 || oaepSeed.length !== 32) throw new Error('Invalid key material length')
    const encrypted = publicKey.encrypt(binary(rawKey), 'RSA-OAEP', {
      md: forge.md.sha256.create(), mgf1: { md: forge.md.sha256.create() }, seed: binary(oaepSeed)
    })
    return forge.util.encode64(encrypted)
  },
  encryptValue(rawKey, iv, aad, value) {
    if (rawKey.length !== 32 || iv.length !== 12) throw new Error('Invalid AES material length')
    const cipher = forge.cipher.createCipher('AES-GCM', binary(rawKey))
    cipher.start({ iv: binary(iv), additionalData: forge.util.encodeUtf8(aad), tagLength: 128 })
    cipher.update(forge.util.createBuffer(forge.util.encodeUtf8(value)))
    if (!cipher.finish()) throw new Error('Payload encryption failed')
    return `enc:v1:${forge.util.encode64(binary(iv))}:${forge.util.encode64(cipher.output.getBytes() + cipher.mode.tag.getBytes())}`
  }
}
