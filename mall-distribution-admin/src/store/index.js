import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clearAdminSessionStorage, resetAdminLoginRedirect, saveAdminSessionExpireTime } from '@/utils/adminSession'

export const useAppStore = defineStore('app', () => {
  // 侧边栏是否折叠
  const sidebarCollapsed = ref(false)

  // 用户信息
  const userInfo = ref({
    id: null,
    username: '',
    nickname: '',
    avatar: '',
    roleCode: '',
  })

  // HttpOnly Cookie 由浏览器管理；前端只保留不含凭证的会话标记。
  const token = ref(localStorage.getItem('admin_session_present') === '1' ? 'cookie-session' : '')
  const expireTime = ref(localStorage.getItem('admin_session_expire_time') || '')
  const permissions = ref([])
  const authHydrated = ref(false)
  // 升级后主动清理旧版遗留；这些字段不再参与会话或权限判定。
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('permissions')

  // 切换侧边栏
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  // 设置Token
  const setToken = () => {
    token.value = 'cookie-session'
    localStorage.setItem('admin_session_present', '1')
    localStorage.removeItem('token')
  }

  const setExpireTime = (value) => {
    expireTime.value = value || ''
    saveAdminSessionExpireTime(expireTime.value)
  }

  // 设置用户信息
  const setUserInfo = (info) => {
    userInfo.value = info || {}
  }

  const setPermissions = (items) => {
    permissions.value = items || []
  }

  const setAuth = (auth) => {
    setToken(auth.token)
    setExpireTime(auth.expireTime)
    setUserInfo(auth.admin)
    setPermissions(auth.permissions)
    authHydrated.value = true
    resetAdminLoginRedirect()
  }

  const hasPermission = (permission) => {
    if (!permission) {
      return true
    }
    return permissions.value.includes('*') || permissions.value.includes(permission)
  }

  const hasAnyPermission = (items) => {
    if (!items || items.length === 0) {
      return true
    }
    return permissions.value.includes('*') || items.some((item) => permissions.value.includes(item))
  }

  // 退出登录
  const logout = () => {
    token.value = ''
    expireTime.value = ''
    userInfo.value = {
      id: null,
      username: '',
      nickname: '',
      avatar: '',
      roleCode: '',
    }
    permissions.value = []
    authHydrated.value = false
    clearAdminSessionStorage()
  }

  return {
    sidebarCollapsed,
    userInfo,
    token,
    expireTime,
    permissions,
    authHydrated,
    toggleSidebar,
    setToken,
    setExpireTime,
    setUserInfo,
    setPermissions,
    setAuth,
    hasPermission,
    hasAnyPermission,
    logout,
  }
})
