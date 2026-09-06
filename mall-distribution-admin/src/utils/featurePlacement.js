const fields = { live: 'liveSquareEnabled', newArrivals: 'newArrivalsEnabled' }
export const placementLabels = { off: '关闭', page: '仅独立页面', home: '页面与首页入口' }
export function featurePlacement(form, type) {
  if (!fields[type] || Number(form?.[fields[type]]) !== 1) return 'off'
  return form.homeModules?.some((module) => module.type === type && module.enabled === true) ? 'home' : 'page'
}
export function setFeaturePlacement(form, type, value) {
  if (!form || !fields[type] || !Object.hasOwn(placementLabels, value)) return
  form[fields[type]] = value === 'off' ? 0 : 1
  // Turning a page off retains content and the old entry preference. A subsequent
  // explicit page/home choice is the only operation changing entry visibility.
  if (value === 'off') return
  form.homeModules ||= []
  let module = form.homeModules.find((entry) => entry.type === type)
  if (!module) { module = { type, sort: form.homeModules.length + 1 }; form.homeModules.push(module) }
  module.enabled = value === 'home'
}
