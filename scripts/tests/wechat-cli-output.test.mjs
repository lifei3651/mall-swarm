import assert from 'node:assert/strict'
import test from 'node:test'

import { hasSuccessfulUploadMarker } from '../lib/wechat-cli-output.mjs'

test('accepts the official upload marker from stdout', () => {
  assert.equal(hasSuccessfulUploadMarker('- 上传\n✔ upload\n', ''), true)
})

test('accepts the official upload marker from stderr used by newer CLI builds', () => {
  assert.equal(hasSuccessfulUploadMarker('', '- 上传\r\n✔ upload\r\n'), true)
})

test('does not accept exit text that only contains upload as a substring', () => {
  assert.equal(hasSuccessfulUploadMarker('upload failed', 'preparing upload'), false)
})
