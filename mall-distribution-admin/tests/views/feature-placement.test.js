import { describe, it, expect } from 'vitest'
import { featurePlacement, setFeaturePlacement } from '../../src/utils/featurePlacement.js'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
describe('直播/新品单一展示位置配置', () => {
  for (const [type, field] of [['live','liveSquareEnabled'],['newArrivals','newArrivalsEnabled']]) {
    it(`${type}三种状态明确且不破坏内容及其他模块`, () => {
      const form = { [field]:1, content:'保留原内容', homeModules:[{type,enabled:true,sort:5},{type:'banner',enabled:true,sort:1}] }
      expect(featurePlacement(form,type)).toBe('home')
      setFeaturePlacement(form,type,'off'); expect(featurePlacement(form,type)).toBe('off'); expect(form.homeModules[0].enabled).toBe(true)
      setFeaturePlacement(form,type,'page'); expect(featurePlacement(form,type)).toBe('page')
      setFeaturePlacement(form,type,'home'); expect(featurePlacement(form,type)).toBe('home')
      expect(form.content).toBe('保留原内容'); expect(form.homeModules[1]).toEqual({type:'banner',enabled:true,sort:1})
      expect(form.homeModules[0].sort).toBe(5)
    })
  }
  it('缺失模块可添加而非法类型和状态不能改动配置', () => {
    const form={}; setFeaturePlacement(form,'live','home'); expect(featurePlacement(form,'live')).toBe('home')
    const before=JSON.stringify(form); setFeaturePlacement(form,'unknown','home'); setFeaturePlacement(form,'live','bad'); expect(JSON.stringify(form)).toBe(before)
  })
  it('分类选项自动预览分类页，手机紧凑版保留两列可读商品', () => {
    const source=readFileSync(resolve(process.cwd(),'src/views/tenant/list.vue'),'utf8')
    expect(source).toContain("template?.value === 'category-focus' ? 'category' : 'home'")
    expect(source).toContain('@click="selectCategoryGuide(template.value)"')
    expect(source).toContain('grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px;')
    expect(source).not.toContain('v-model="displayForm.liveSquareEnabled"')
    expect(source).not.toContain('v-model="displayForm.newArrivalsEnabled"')
  })
})
