import { beforeEach, describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { updateAdminBrowserLogo } from '@/utils/adminBrand'

describe('后台浏览器品牌图标', () => {
  beforeEach(() => {
    document.head.innerHTML = ''
  })

  it('默认使用与商城前台一致的灵启图标', async () => {
    const index = await readFile(resolve(process.cwd(), 'index.html'), 'utf8')
    expect(index).toContain('/src/assets/lingqi-logo-mark.png')
    expect(index).not.toContain('/lingqi-logo.svg')

    updateAdminBrowserLogo('')
    const favicon = document.head.querySelector('link[rel="icon"]')
    const appleIcon = document.head.querySelector('link[rel="apple-touch-icon"]')
    expect(favicon?.href).toBeTruthy()
    expect(appleIcon?.href).toBe(favicon?.href)
  })

  it('客户配置Logo后同步更新后台标签页和桌面图标', () => {
    const customerLogo = 'https://cdn.example.com/customer-logo.png'
    updateAdminBrowserLogo(customerLogo)
    expect(document.head.querySelector('link[rel="icon"]')?.href).toBe(customerLogo)
    expect(document.head.querySelector('link[rel="apple-touch-icon"]')?.href).toBe(customerLogo)
  })
})
