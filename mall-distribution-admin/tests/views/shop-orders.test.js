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
})
