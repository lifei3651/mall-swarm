import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { runMiniScript } from './run-mini-script.mjs'

const root = fileURLToPath(new URL('../..', import.meta.url))
const clone = value => JSON.parse(JSON.stringify(value))
export function commerceEnv(respond = () => ({}), token = 'member') {
  const storage = new Map([['mall_mini_access_token', token], ['mall_mini_member', { id: '1' }]]), cache = new Map()
  const calls = [], notices = [], routes = []
  let definition, tabHidden = false
  const wx = {
    getStorageSync: key => storage.has(key) ? clone(storage.get(key)) : undefined,
    setStorageSync: (key, value) => storage.set(key, clone(value)), removeStorageSync: key => storage.delete(key),
    showToast: item => notices.push(item.title), showModal: item => { notices.push(item.content); item.success?.({ confirm: true }) },
    navigateTo: item => routes.push(item.url), switchTab: item => routes.push(item.url), setNavigationBarTitle() {}, stopPullDownRefresh() {}
  }
  function load(name, parent = root) {
    const file = resolve(parent, name.endsWith('.js') ? name : name + '.js')
    if (file === resolve(root, 'utils/request.js')) return async options => { calls.push(clone(options)); return respond(options) }
    if (file === resolve(root, 'utils/share.js')) return { prepare() {}, hide() {} }
    if (file === resolve(root, 'utils/theme.js')) return { pageData: () => ({}), apply() {}, sync() {}, remember: () => ({}) }
    if (cache.has(file)) return cache.get(file).exports
    const module = { exports: {} }; cache.set(file, module)
    runMiniScript(readFileSync(file, 'utf8'), { module, exports: module.exports,
      require: id => load(id, dirname(file)), wx, Page: value => { definition = value }, getCurrentPages: () => [], setTimeout, clearTimeout })
    return module.exports
  }
  function page(name) {
    load(`pages/${name}/index`)
    return { ...definition, data: clone(definition.data), getTabBar: () => ({ setData: ({ hidden }) => { tabHidden = hidden } }), setData(patch, done) {
      for (const [key, value] of Object.entries(clone(patch))) {
        const parts = key.replace(/\[(\d+)\]/g, '.$1').split('.'); let target = this.data
        for (const part of parts.slice(0, -1)) target = target[part]
        target[parts.at(-1)] = value
      }
      done?.()
    } }
  }
  return { load, page, calls, notices, routes, storage, wx, token: value => storage.set('mall_mini_access_token', value), get tabHidden() { return tabHidden } }
}
