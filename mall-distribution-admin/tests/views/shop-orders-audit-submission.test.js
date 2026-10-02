import { describe, expect, it, vi } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import vm from 'node:vm'
import { flushPromises } from '@vue/test-utils'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const loadAudit = async () => {
  const source = await readFile(resolve(process.cwd(), 'src/views/shop/orders.vue'), 'utf8')
  const start = source.indexOf('const openAudit =')
  const end = source.indexOf('const confirmReturnReceived =')
  expect(start).toBeGreaterThan(0)
  expect(end).toBeGreaterThan(start)
  const context = {
    auditSubmitting: { value: false }, auditDialogVisible: { value: true },
    currentAfterSale: { value: { id: 41, applyType: 1 } },
    auditForm: { value: { status: 1, auditRemark: '核对通过', auditUserId: 2, auditUserName: '审核员' } },
    currentOperator: { value: { id: 2, name: '审核员' } }, computed: fn => ({ get value() { return fn() } }),
    ElMessage: { success: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
    auditShopAfterSale: vi.fn().mockResolvedValue({}), fetchOrders: vi.fn().mockResolvedValue(), fetchWorkSummary: vi.fn().mockResolvedValue(),
  }
  vm.runInNewContext(source.slice(start, end) + '\nthis.actions = { openAudit, submitAudit }', context)
  return context
}

describe('售后审核提交保护（原页面函数，模拟确认及接口）', () => {
  it('确认尚未完成时重复点击只弹一次；禁止改开另一笔审核并提交原目标和备注', async () => {
    const c = await loadAudit()
    const confirmation = deferred()
    c.ElMessageBox.confirm.mockReturnValue(confirmation.promise)
    const pending = c.actions.submitAudit()
    const duplicate = c.actions.submitAudit()
    expect(c.ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    c.actions.openAudit({ id: 99, applyType: 3 }, 2)
    expect(c.currentAfterSale.value.id).toBe(41)
    c.currentAfterSale.value = { id: 100, applyType: 3 }
    c.auditForm.value.auditRemark = '后改备注'
    c.auditForm.value.status = 2
    confirmation.resolve('confirm')
    await Promise.all([pending, duplicate])
    expect(c.ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(c.auditShopAfterSale).toHaveBeenCalledExactlyOnceWith(41, expect.objectContaining({ status: 1, auditRemark: '核对通过' }))
    expect(c.auditSubmitting.value).toBe(false)
    expect(c.auditDialogVisible.value).toBe(false)
  })

  it('接口和刷新尚未完成时仍阻止重复请求', async () => {
    const c = await loadAudit()
    const response = deferred(), refresh = deferred()
    c.auditShopAfterSale.mockReturnValue(response.promise)
    c.fetchOrders.mockReturnValue(refresh.promise)
    const pending = c.actions.submitAudit()
    await flushPromises()
    const duplicate = c.actions.submitAudit()
    await flushPromises()
    expect(c.auditShopAfterSale).toHaveBeenCalledTimes(1)
    response.resolve({})
    await flushPromises()
    const duringRefresh = c.actions.submitAudit()
    expect(c.auditShopAfterSale).toHaveBeenCalledTimes(1)
    expect(c.auditSubmitting.value).toBe(true)
    refresh.resolve()
    await Promise.all([pending, duplicate, duringRefresh])
    expect(c.auditSubmitting.value).toBe(false)
  })

  it.each(['cancel', 'close'])('确认%s后不发请求，保留草稿并可再次确认', async action => {
    const c = await loadAudit()
    c.ElMessageBox.confirm.mockRejectedValueOnce(action)
    await c.actions.submitAudit()
    expect(c.auditShopAfterSale).not.toHaveBeenCalled()
    expect(c.auditSubmitting.value).toBe(false)
    expect(c.auditDialogVisible.value).toBe(true)
    expect(c.auditForm.value.auditRemark).toBe('核对通过')
    await c.actions.submitAudit()
    expect(c.auditShopAfterSale).toHaveBeenCalledTimes(1)
  })

  it('接口失败后解除忙碌状态并保留对话框，允许重试', async () => {
    const c = await loadAudit()
    c.auditShopAfterSale.mockRejectedValueOnce(new Error('network failed'))
    await expect(c.actions.submitAudit()).rejects.toThrow('network failed')
    expect(c.auditSubmitting.value).toBe(false)
    expect(c.auditDialogVisible.value).toBe(true)
    expect(c.ElMessage.success).not.toHaveBeenCalled()
    await c.actions.submitAudit()
    expect(c.auditShopAfterSale).toHaveBeenCalledTimes(2)
  })

  it('拒绝、关闭仍须原因，换货确认仍保留不退款说明', async () => {
    const c = await loadAudit()
    c.auditForm.value = { status: 2, auditRemark: ' ' }
    await c.actions.submitAudit()
    expect(c.ElMessage.warning).toHaveBeenCalledWith('请填写拒绝原因')
    c.auditForm.value.status = 3
    await c.actions.submitAudit()
    expect(c.ElMessage.warning).toHaveBeenCalledWith('请填写关闭原因')
    expect(c.ElMessageBox.confirm).not.toHaveBeenCalled()
    c.auditForm.value = { status: 1, auditRemark: '' }
    c.currentAfterSale.value.applyType = 3
    await c.actions.submitAudit()
    expect(c.ElMessageBox.confirm.mock.calls[0][0]).toContain('不会退款或重算奖金')
  })
})
