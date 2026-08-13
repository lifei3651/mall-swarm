import { describe, expect, it } from 'vitest'
import { logisticsCompanyOptions } from '../src/utils/logisticsCompanies'

describe('物流公司标准选项', () => {
  it('包含主要承运商且名称不重复', () => {
    expect(logisticsCompanyOptions).toEqual(expect.arrayContaining([
      '顺丰速运',
      '京东物流',
      '中通快递',
      '圆通速递',
      '申通快递',
      '韵达快递',
      '极兔速递',
      '中国邮政',
      'EMS',
      '德邦快递',
    ]))
    expect(new Set(logisticsCompanyOptions).size).toBe(logisticsCompanyOptions.length)
  })
})
