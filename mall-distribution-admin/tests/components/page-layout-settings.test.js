import { describe, it, expect, vi } from 'vitest'
vi.unmock('element-plus')
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PageLayoutSettings from '../../src/components/PageLayoutSettings.vue'
import { normalizePageLayouts } from '../../../mall-shop-web/src/utils/pageLayouts.js'
describe('page layout controls', () => {
  it('only edits selected page/platform and clearing override restores inheritance', async () => {
    const initial = normalizePageLayouts({ layoutTemplate: 'standard' })
    const wrapper = mount(PageLayoutSettings, { props: { modelValue: initial }, global: { plugins: [ElementPlus] } })
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'mini')
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('change', 'mini')
    await wrapper.setProps({ platform: 'mini' })
    await wrapper.vm.$nextTick()
    wrapper.findAllComponents({ name: 'ElSelect' })[1].vm.$emit('change', 'showcase')
    const updated = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(updated.platforms.mini).toEqual({ category: 'showcase' })
    expect(updated.shared).toEqual(initial.shared)
    expect(initial.platforms.mini).toEqual({})
    await wrapper.setProps({ modelValue: updated })
    wrapper.findAllComponents({ name: 'ElSelect' })[1].vm.$emit('change', 'inherit')
    expect(wrapper.emitted('update:modelValue').at(-1)[0].platforms.mini).toEqual({})
    expect(wrapper.emitted('preview').at(-1)[0]).toEqual({ platform: 'mini', page: 'category' })
    wrapper.unmount()
  })
})
