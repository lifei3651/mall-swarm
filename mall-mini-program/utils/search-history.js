const KEY = 'mall_mini_recent_searches'
function list() { try { const rows = wx.getStorageSync(KEY); return Array.isArray(rows) ? rows.filter(value => typeof value === 'string' && value.trim()).slice(0,5).map(value=>value.slice(0,100)) : [] } catch (_) { return [] } }
function remember(value) { const keyword = String(value || '').trim().slice(0,100), rows = keyword ? [keyword,...list().filter(item=>item!==keyword)].slice(0,5) : list(); try { wx.setStorageSync(KEY, rows) } catch (_) {} return rows }
// Do not silently report success when local storage cannot be changed.
function clear() { wx.removeStorageSync(KEY); return [] }
module.exports = { list, remember, clear }
