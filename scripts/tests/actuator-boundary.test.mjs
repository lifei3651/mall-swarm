import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const read = (relativePath) => readFileSync(path.join(root, relativePath), 'utf8')

test('production metrics remain opt-in on a loopback-bound backend', () => {
  const production = read('mall-distribution/src/main/resources/application-prod.yml')
  assert.match(production, /address:\s*\$\{SERVER_ADDRESS:127\.0\.0\.1\}/)
  assert.match(production, /forward-headers-strategy:\s*framework/)
  assert.match(production, /include:\s*\$\{MANAGEMENT_ENDPOINTS:health\}/)
  assert.match(read('mall-distribution/pom.xml'), /<artifactId>micrometer-registry-prometheus<\/artifactId>/)
})

test('public production gateway never proxies actuator endpoints', () => {
  const nginx = read('scripts/nginx/lingqimall.conf')
  assert.match(nginx, /location = \/api\/actuator \{\s*return 404;/)
  assert.match(nginx, /location \^~ \/api\/actuator\/ \{\s*return 404;/)
  assert.match(nginx, /location \^~ \/api\/v1\/actuator\/ \{\s*return 404;/)
})

test('private deployment exposes backend only on Docker networks and blocks public actuator routes', () => {
  const compose = read('document/private-deploy/docker-compose.private.yml')
  const backend = compose.split(/^  mall-distribution:\s*$/m)[1]?.split(/^  nginx:\s*$/m)[0]
  assert.ok(backend, 'mall-distribution service exists')
  assert.doesNotMatch(backend, /^\s+ports:/m)
  assert.match(backend, /SERVER_ADDRESS:\s*0\.0\.0\.0/)
  assert.match(backend, /networks:\s*\n\s*- edge\s*\n\s*- data/)
  const api = read('document/private-deploy/nginx/includes/shop-api.conf')
  assert.match(api, /location ~ [^\n]*actuator[^\n]*\{\s*return 404;/)
})
