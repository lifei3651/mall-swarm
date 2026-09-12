import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import Reviews from '../../src/views/shop/reviews.vue'

const api = vi.hoisted(() => ({
  listProductReviews: vi.fn(), listShopProducts: vi.fn(), updateProductReviewStatus: vi.fn(), replyProductReview: vi.fn(),
  store: { userInfo: { merchantId: null } },
}))
vi.mock('@/api/shop', () => api)
vi.mock('@/store', () => ({ useAppStore: () => api.store }))
const row = () => ({ id: 7, productId: 1, productName: '本地测试商品', reviewerName: '测试买家', rating: 4,
  content: '真实评价', status: 1, merchantReply: '商家原回复', platformReply: '平台原回复', merchantReplyVersion: 1, platformReplyVersion: 2 })
const mounted = () => mount(Reviews, { global: { plugins: [ElementPlus] } })

beforeEach(() => {
  vi.clearAllMocks()
  api.store.userInfo.merchantId = null
  api.listProductReviews.mockResolvedValue({ data: { list: [row()], total: 1 } })
  api.listShopProducts.mockResolvedValue({ data: { list: [], total: 0 } })
  api.replyProductReview.mockResolvedValue({ data: true })
})

describe('评价回复编辑', () => {
  it('平台编辑自己的回复，两方原文分别显示；保存不发送身份字段', async () => {
    const wrapper = mounted(); await flushPromises()
    expect(wrapper.text()).toContain('商家回复')
    expect(wrapper.text()).toContain('平台回复')
    wrapper.vm.openReply(row())
    expect(wrapper.vm.replyContent).toBe('平台原回复')
    wrapper.vm.replyContent = '  平台的新回复  '
    await wrapper.vm.saveReply()
    expect(api.replyProductReview).toHaveBeenCalledWith(7, { content: '平台的新回复', expectedVersion: 2 })
    expect(wrapper.vm.replyVisible).toBe(false)
    wrapper.unmount()
  })

  it('商家只能编辑商家回复，不显示隐藏评价操作', async () => {
    api.store.userInfo.merchantId = 81
    const wrapper = mounted(); await flushPromises()
    wrapper.vm.openReply(row())
    expect(wrapper.vm.replyContent).toBe('商家原回复')
    expect(wrapper.findAll('button').some(button => button.text() === '隐藏')).toBe(false)
    wrapper.vm.replyContent = '商家补充说明'
    await wrapper.vm.saveReply()
    expect(api.replyProductReview).toHaveBeenCalledWith(7, { content: '商家补充说明', expectedVersion: 1 })
    wrapper.unmount()
  })

  it('失败保留输入，空内容和隐藏评价不能提交，提交中阻止重复点击', async () => {
    const wrapper = mounted(); await flushPromises()
    wrapper.vm.openReply({ ...row(), status: 0 })
    expect(wrapper.vm.replyVisible).toBe(false)
    wrapper.vm.openReply(row())
    wrapper.vm.replyContent = '   '
    await wrapper.vm.saveReply()
    expect(api.replyProductReview).not.toHaveBeenCalled()
    wrapper.vm.replyContent = '保留草稿'
    let reject
    api.replyProductReview.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
    const first = wrapper.vm.saveReply()
    await wrapper.vm.saveReply()
    expect(api.replyProductReview).toHaveBeenCalledTimes(1)
    reject(Error('回复已更新，请刷新'))
    await first
    expect(wrapper.vm.replyContent).toBe('保留草稿')
    expect(wrapper.vm.replyVisible).toBe(true)
    expect(wrapper.vm.replyError).toContain('刷新')
    expect(wrapper.vm.replySaving).toBe(false)
    wrapper.unmount()
  })

  it('回复与评价内容按纯文本渲染，不执行 HTML', async () => {
    api.listProductReviews.mockResolvedValue({ data: { list: [{ ...row(), merchantReply: '<img src=x onerror=alert(1)>' }], total: 1 } })
    const wrapper = mounted(); await flushPromises()
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('.review-reply img').exists()).toBe(false)
    wrapper.unmount()
  })
})
