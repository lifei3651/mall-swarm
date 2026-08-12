import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clearAdminSessionStorage, saveAdminSessionExpireTime } from '@/utils/adminSession'

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

  // Token
  const legacyToken = localStorage.getItem('token') || ''
  const token = ref(legacyToken || (localStorage.getItem('admin_session_present') === '1' ? 'cookie-session' : ''))
  const expireTime = ref(localStorage.getItem('admin_session_expire_time') || '')
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || '[]'))
  const cachedUser = localStorage.getItem('userInfo')
  if (cachedUser) {
    userInfo.value = JSON.parse(cachedUser)
  }

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
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  const setPermissions = (items) => {
    permissions.value = items || []
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  const setAuth = (auth) => {
    setToken(auth.token)
    setExpireTime(auth.expireTime)
    setUserInfo(auth.admin)
    setPermissions(auth.permissions)
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
    clearAdminSessionStorage()
  }

  return {
    sidebarCollapsed,
    userInfo,
    token,
    expireTime,
    permissions,
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
