import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/members.vue')

describe('会员全景直属邀请人信息', () => {
  it('只展示邀请人会员名称和手机号，不展示邀请人登录账号', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('label="邀请人会员名称"')
    expect(source).toContain('label="邀请人手机号"')
    expect(source).toContain("手机号：{{ row.inviterPhone || '未设置' }}")
    expect(source).not.toContain('label="邀请人登录账号"')
    expect(source).not.toContain('账号：${account}')
  })

  it('订单区只表达有效订单并准确区分已售后与已关闭', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('<template #header>有效订单</template>')
    expect(source).toContain("hasAfterSale(row) ? '已售后' : '已关闭'")
    expect(source).not.toContain("4: '售后关闭'")
  })
})

describe('会员余额调整表单', () => {
  it('打开表单时默认显示0元，实际提交仍要求金额大于0', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('v-model="assetForm.amount" :min="0" :precision="2"')
    expect(source).toContain('amount: 0,')
    expect(source).toContain("Number(assetForm.value.amount) <= 0")
    expect(source).toContain("请输入大于0的调整数量")
  })
})

describe('会员账号锁定解除', () => {
  it('登录密码和支付密码锁定分别提供后台解除入口', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('v-if="currentMember.loginLocked"')
    expect(source).toContain('解除登录锁定')
    expect(source).toContain('v-if="currentMember.paymentPasswordLocked"')
    expect(source).toContain('解除支付密码锁定')
    expect(source).toContain('unlockShopMemberPaymentPassword(row.id)')
  })

  it('禁用或启用会员前说明影响并要求二次确认', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('ElMessageBox.confirm')
    expect(source).toContain('禁用后该会员将无法登录和下单')
    expect(source).toContain('历史订单与售后记录会保留')
    expect(source).toContain("confirmButtonText: disabling ? '确认禁用' : '确认启用'")
  })
})
