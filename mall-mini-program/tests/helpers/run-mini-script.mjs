import vm from 'node:vm'
import { readFileSync } from 'node:fs'

// Page unit tests acknowledge informational dialogs immediately. Destructive
// confirmations still go through each test's own wx.showModal mock unchanged.
// feedback.test.mjs exercises the real modal queue without this adapter.
export function runMiniScript(source, sandbox, options) {
  const originalRequire = sandbox.require
  const cache = new Map()
  sandbox = { ...sandbox, require(id) {
    const name = /(?:^|\/)(feedback|transport-error)$/.exec(id)?.[1]
    if (!name) return originalRequire(id)
    if (!cache.has(name)) {
      const module = { exports: {} }
      vm.runInNewContext(readFileSync(new URL(`../../utils/${name}.js`, import.meta.url), 'utf8'), {
        module, wx: { showModal(dialog) {
          sandbox.wx?.showToast?.({ title: dialog.content, icon: dialog.title === '操作完成' ? 'success' : 'none' })
          dialog.success?.({ confirm: true })
        } }
      })
      cache.set(name, module.exports)
    }
    return cache.get(name)
  } }
  return vm.runInNewContext(source, sandbox, options)
}
