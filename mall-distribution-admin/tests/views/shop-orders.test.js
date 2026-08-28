import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/orders.vue')

describe('商城订单取消入口', () => {
  it('待付款和待发货订单都显示取消操作，待发货明确提示退款', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('[0, 1].includes(Number(row?.order?.status))')
    expect(source).toContain("'取消并退款'")
    expect(source).toContain('系统会原路全额退款、关闭订单并恢复库存')
  })

  it('全部订单不展示已取消或已拒绝的售后卡片，履约状态与有效退款结果分栏', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).not.toContain('v-for="sale in row.afterSales"')
    expect(source).not.toContain("sale.reason || '未填写原因'")
    expect(source).toContain('label="履约状态"')
    expect(source).toContain('label="售后 / 退款"')
    expect(source).toContain('[0, 4, 5, 6].includes(Number(item.status))')
    expect(source).toContain('Number(item.status) === 1')
    expect(source).toContain("'全额退款' : '部分退款'")
    expect(source).toContain('实退 ¥')
  })

  it('主操作优先处理售后和发货，奖金、取消及后台退款收进更多操作', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('处理售后')
    expect(source).toContain('更多操作')
    expect(source).toContain('handleAfterSaleCommand')
    expect(source).toContain('handleOrderMoreCommand')
    expect(source).toContain('canCancelAdminOrder')
    expect(source).toContain('command="BONUS"')
  })

  it('物流批量发货同时提供预填发货表和独立空白导入模板', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('下载发货表')
    expect(source).toContain('下载导入模板')
    expect(source).toContain('downloadOrderShipmentImportTemplate')
    expect(source).toContain('物流发货导入模板.xlsx')
  })

  it('人工发货使用可搜索的标准物流公司下拉选择', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('v-model="shipForm.deliveryCompany"')
    expect(source).toContain('placeholder="请选择物流公司"')
    expect(source).toContain('v-for="company in logisticsCompanyOptions"')
    expect(source).toContain("from '@/utils/logisticsCompanies'")
    expect(source).not.toContain('<el-input v-model="shipForm.deliveryCompany"')
  })

  it('订单列表显示每个包裹件数和预计自动收货时间', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('shipment.shipmentQuantity || 0')
    expect(source).toContain('row.autoReceiveEnabled')
    expect(source).toContain('row.autoReceiveDeadline')
    expect(source).toContain('自动收货')
  })

  it('售后展示下一责任时限，超时升级平台介入但不自动退款', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('nextActionHint')
    expect(source).toContain('nextActionDeadline')
    expect(source).toContain('nextActionOverdue')
    expect(source).toContain('超时只升级为平台优先介入，不会自动退款')
    expect(source).toContain('拒绝或关闭必须说明具体原因')
  })

  it('待发货与售后使用独立数字提醒，售后队列不再展示发货入口', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('getAdminOrderWorkSummary')
    expect(source).toContain('order-state-count')
    expect(source).toContain("PENDING_SHIPMENT: Number(orderWorkSummary.value.pendingShipment")
    expect(source).toContain("AFTER_SALE: Number(orderWorkSummary.value.afterSale")
    expect(source).toContain("{ label: '待售后', value: 'AFTER_SALE' }")
    expect(source).toContain('route.query.orderState')
    expect(source).toContain('v-if="canShipOrder(row)"')
    expect(source).not.toContain(':disabled="!canShipOrder(row)"')
    expect(source).toContain('审核通过')
    expect(source).toContain('等待客户寄回')
    expect(source).toContain('确认退货并退款')
    expect(source).toContain("query.orderState === 'PENDING_SHIPMENT'")
  })

  it('订单末尾提供仅后台可见的客服备注并保留客户原留言', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('label="客服备注"')
    expect(source).toContain('fixed="right"')
    expect(source).toContain('openServiceRemark(row)')
    expect(source).toContain('updateShopOrderServiceRemark')
    expect(source).toContain('此备注仅供后台客服和运营人员查看，不会展示给下单客户')
    expect(source).toContain("currentOrder?.order?.remark || '客户未填写留言'")
    expect(source).toContain('maxlength="500"')
    expect(source).toContain('手机号或客服备注')
  })

  it('平台后台明确标识商户子订单并可按联合支付单号搜索', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('联合支付单号')
    expect(source).toContain('row.order?.tradeNo')
    expect(source).toContain('联合支付 {{ row.order.tradeNo }}')
    expect(source).toContain('商户子订单')
    expect(source).toContain('查看联合单')
    expect(source).toContain('getShopTradeDetail')
    expect(source).toContain('title="联合支付详情"')
    expect(source).toContain('tradeDetail.childOrders')
  })

  it('商户工作台保留履约售后并隐藏平台资金及跨商户操作', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('v-if="isMerchantUser"')
    expect(source).toContain('这里只显示本商户的履约子订单')
    expect(source).toContain('v-if="!isMerchantUser" label="奖金总拨出"')
    expect(source).toContain('v-if="!isMerchantUser" trigger="click"')
    expect(source).toContain('!isMerchantUser.value && !hasPendingAfterSale(row)')
    expect(source).toContain('v-if="canShipOrder(row)"')
    expect(source).toContain('处理售后')
    expect(source).toContain('row.merchantFulfillmentAllowed === false')
    expect(source).toContain('canMerchantFulfill(row)')
    expect(source).toContain('当前履约已由平台接管或冻结')
  })

  it('后台退款遵循服务端售后配置，不再自行写死下单后7天', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('row?.afterSaleDeadline')
    expect(source).toContain('row?.afterSaleSelfServiceEnabled === false')
    expect(source).toContain('isCustomerAfterSaleClosed(row)')
    expect(source).toContain('客户自助售后入口已关闭，后台根据客服协商处理')
    expect(source).not.toContain('created + 7 * 24 * 60 * 60 * 1000')
    expect(source).not.toContain('订单超过前台7天售后期限')
  })

  it('售后审核弹窗展示会员提交的私密图片凭证', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('label="图片凭证"')
    expect(source).toContain('afterSaleProofUrls(currentAfterSale)')
    expect(source).toContain('/api/shop/admin/after-sales/proofs/')
    expect(source).toContain('preview-teleported')
  })

  it('退款金额和售后状态变更在前端提供资金操作保护', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain(':max="Math.max(0.01, manualRefundRemainingAmount)"')
    expect(source).toContain('商品退款金额不能超过剩余可退金额')
    expect(source).toContain("[1, 6].includes(Number(sale.status))")
    expect(source).toContain('确认售后处理')
    expect(source).toContain('并可能立即执行退款和账务冲销')
    expect(source).toContain('请填写验收备注')
    expect(source).toContain('auditRemark: auditRemark.trim()')
  })
})
