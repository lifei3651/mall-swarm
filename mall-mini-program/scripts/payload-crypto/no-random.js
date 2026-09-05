const forge = require('node-forge/lib/forge')
function unavailable() { throw new Error('Implicit forge randomness is disabled; caller must provide platform CSPRNG material') }
module.exports = forge.random = { getBytes: unavailable, getBytesSync: unavailable, createInstance: unavailable }
