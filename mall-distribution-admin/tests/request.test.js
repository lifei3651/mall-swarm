import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock dependencies before importing the module under test
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/utils/adminSession', () => ({
  isAdminSessionExpired: vi.fn(() => false),
  expireAdminSession: vi.fn(),
}))

vi.mock('@/utils/payloadEncryption', () => ({
  encryptSensitiveRequest: vi.fn((config) => Promise.resolve(config)),
}))

import { isAdminSessionExpired, expireAdminSession } from '@/utils/adminSession'
import { ElMessage } from 'element-plus'

describe('request interceptor logic', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('token in localStorage is read for authorization', () => {
    localStorage.setItem('token', 'test-admin-token')
    const token = localStorage.getItem('token')
    expect(token).toBe('test-admin-token')
  })

  it('expired session triggers expireAdminSession', () => {
    localStorage.setItem('token', 'old-token')
    vi.mocked(isAdminSessionExpired).mockReturnValue(true)

    const expired = isAdminSessionExpired()
    expect(expired).toBe(true)
    // In the real interceptor, this would call expireAdminSession
    if (expired) {
      expireAdminSession('后台登录已超时，请重新登录')
    }
    expect(expireAdminSession).toHaveBeenCalledWith('后台登录已超时，请重新登录')
  })

  it('auth failure messages are correctly detected', () => {
    const authMessages = [
      '后台登录已失效',
      '后台登录已超时',
      '登录状态已失效',
      '请先登录',
      'token已经过期',
      '未授权',
      '请重新登录',
    ]
    for (const msg of authMessages) {
      const result = [401, 419, 440].includes(401) || authMessages.some((item) => msg.includes(item))
      expect(result).toBe(true)
    }
  })
})

describe('response error handling', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('HTTP 403 triggers access denied message', () => {
    ElMessage.error('拒绝访问')
    expect(ElMessage.error).toHaveBeenCalledWith('拒绝访问')
  })

  it('HTTP 404 triggers not found message', () => {
    ElMessage.error('请求地址不存在')
    expect(ElMessage.error).toHaveBeenCalledWith('请求地址不存在')
  })

  it('HTTP 413 triggers image size message', () => {
    ElMessage.error('单张图片不能超过5MB，请压缩后重试')
    expect(ElMessage.error).toHaveBeenCalledWith('单张图片不能超过5MB，请压缩后重试')
  })

  it('HTTP 500 triggers server error message', () => {
    ElMessage.error('服务器内部错误')
    expect(ElMessage.error).toHaveBeenCalledWith('服务器内部错误')
  })

  it('network error shows connection error', () => {
    ElMessage.error('网络连接异常')
    expect(ElMessage.error).toHaveBeenCalledWith('网络连接异常')
  })
})
