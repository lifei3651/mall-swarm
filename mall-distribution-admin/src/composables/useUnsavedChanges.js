import { onBeforeUnmount, onMounted } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'

/** 保护真正会造成运营配置丢失的长表单；搜索筛选等短暂状态不使用。 */
export function useUnsavedChanges(dirty, message = '当前页面有尚未保存的修改，确定离开吗？') {
  const beforeUnload = (event) => {
    if (!dirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
  onBeforeRouteLeave(async () => {
    if (!dirty.value) return true
    try {
      await ElMessageBox.confirm(message, '未保存的修改', {
        type: 'warning', confirmButtonText: '放弃修改并离开', cancelButtonText: '继续编辑',
      })
      return true
    } catch {
      return false
    }
  })
}
