import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, inject, provide, toRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WeChatShippingPanel from '@/views/tenant/wechat-shipping-panel.vue'
import { listShippingSynchronizations, requeueShippingSynchronization } from '@/api/messageOperation'

vi.mock('@/api/messageOperation', () => ({ listShippingSynchronizations: vi.fn(), requeueShippingSynchronization: vi.fn() }))
const task = { id: '9007199254740993', paymentNoHint: '***123456', status: 'PERMANENT', revision: 7, canRetry: true, errorCode: 'WECHAT_40001' }
const result = (enabled = true, rows = [task]) => ({ data: { enabled, failedCount: 1, tasks: { list: rows, total: rows.length } } })
const ElTable = defineComponent({ props: ['data'], setup(props, { slots }) { provide('rows', toRef(props, 'data')); return () => h('div', slots.default?.()) } })
const ElTableColumn = defineComponent({ props: ['prop'], setup(props, { slots }) { const rows = inject('rows'); return () => h('div', rows.value.map(row => slots.default ? slots.default({ row }) : String(row[props.prop] || ''))) } })
const stubs = {
  ElTable, ElTableColumn,
  ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled"><slot/></button>' },
  ElAlert: { props: ['title'], template: '<p>{{title}}</p>' },
  ElSelect: { template: '<select><slot/></select>' }, ElOption: true, ElPagination: true
}
async function render() { const wrapper = mount(WeChatShippingPanel, { global: { stubs, directives: { loading() {} } } }); await flushPromises(); return wrapper }
beforeEach(() => {
  vi.clearAllMocks()
  listShippingSynchronizations.mockResolvedValue(result())
  requeueShippingSynchronization.mockResolvedValue({ data: null })
  ElMessageBox.confirm.mockResolvedValue('confirm')
})
describe('微信发货同步台账交互', () => {
  it('读取失败与永久失败可见；不把排队当成同步成功', async () => {
    const wrapper = await render()
    expect(listShippingSynchronizations).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20, status: undefined })
    expect(wrapper.text()).toContain('1 笔微信发货同步永久失败')
    expect(wrapper.text()).toContain('***123456')
    expect(wrapper.text()).toContain('WECHAT_40001')
    listShippingSynchronizations.mockRejectedValueOnce(new Error('internal private details'))
    await wrapper.get('[data-test="refresh"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('台账读取失败，请重试')
    expect(wrapper.text()).not.toContain('internal private details')
    wrapper.unmount()
  })
  it('只有确认后按字符串任务编号和已读版本重新排队，随后刷新台账', async () => {
    const wrapper = await render()
    await wrapper.get(`[data-test="requeue-${task.id}"]`).trigger('click'); await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(requeueShippingSynchronization).toHaveBeenCalledWith('9007199254740993', 7)
    expect(listShippingSynchronizations).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalledWith('已重新排队，实际同步结果请刷新台账查看')
    wrapper.unmount()
  })
  it('取消、门禁关闭、无可重试状态均不产生重排请求', async () => {
    ElMessageBox.confirm.mockRejectedValueOnce('cancel')
    let wrapper = await render()
    await wrapper.get(`[data-test="requeue-${task.id}"]`).trigger('click'); await flushPromises()
    expect(requeueShippingSynchronization).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
    wrapper.unmount()
    listShippingSynchronizations.mockResolvedValue(result(false))
    wrapper = await render()
    expect(wrapper.find(`[data-test="requeue-${task.id}"]`).exists()).toBe(false)
    expect(wrapper.text()).toContain('门禁未开启')
    wrapper.unmount()
    listShippingSynchronizations.mockResolvedValue(result(true, [{ ...task, status: 'SUCCESS', canRetry: false }]))
    wrapper = await render()
    expect(wrapper.find(`[data-test="requeue-${task.id}"]`).exists()).toBe(false)
    expect(requeueShippingSynchronization).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('确认弹窗未完成时阻止重复点击；服务端拒绝不显示成功', async () => {
    let confirm
    ElMessageBox.confirm.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    requeueShippingSynchronization.mockRejectedValueOnce(new Error('revision conflict'))
    const wrapper = await render()
    const button = wrapper.get(`[data-test="requeue-${task.id}"]`)
    await button.trigger('click'); await button.trigger('click')
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(requeueShippingSynchronization).not.toHaveBeenCalled()
    confirm(); await flushPromises()
    expect(requeueShippingSynchronization).toHaveBeenCalledTimes(1)
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('未能重新排队，请刷新状态后重试')
    wrapper.unmount()
  })
})
