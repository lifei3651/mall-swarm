<template>
  <div class="page-container">
    <el-card>
      <template #header><span>外部团队整体平移</span></template>

      <el-alert title="这是外部团队迁入，不是商城内部切线" type="warning" :closable="false" show-icon>
        <template #default>
          <p>系统为每个人创建唯一商城登录账号，并按“外部会员编号 / 外部上级编号”重建完整团队树。</p>
          <p>原平台每个人的历史累计有效商品件数、个人业绩、团队业绩作为期初基线保留，<strong>不补发历史奖金</strong>；迁入后的新订单才按当前新零售规则计算奖金与自动升级。</p>
          <p>整批在一个事务中执行：任意手机号、外部编号或上下级关系有错误，整批回滚，不会只导入一半。</p>
        </template>
      </el-alert>

      <el-form label-width="130px" class="migration-form">
        <el-form-item label="商城承接上级">
          <el-select v-model="anchorAgentId" filterable remote clearable :remote-method="searchAgents" placeholder="留空则外部根成员成为商城根节点" style="width: 420px">
            <el-option v-for="item in agentOptions" :key="item.id" :value="item.id" :label="`${item.agentName}（${item.agentCode}）`" />
          </el-select>
          <div class="help">选择后，文件中没有“外部上级编号”的根成员将统一接到此代理下面。</div>
        </el-form-item>
      </el-form>

      <el-button type="primary" @click="downloadTemplate"><el-icon><Download /></el-icon>下载平移模板</el-button>

      <el-upload ref="uploadRef" class="upload-section" drag :auto-upload="false" :limit="1" :on-exceed="handleExceed" :on-change="handleChange" accept=".xlsx,.xls,.csv">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择文件</em></div>
        <template #tip><div class="el-upload__tip">列顺序必须与模板一致，支持 .xlsx / .xls / .csv，单次最多 1000 人。</div></template>
      </el-upload>

      <el-button type="success" :loading="loading" :disabled="!file" @click="handleImport"><el-icon><Upload /></el-icon>开始整体平移</el-button>

      <div v-if="loading" class="migration-progress">
        <div class="progress-title">
          <span>{{ uploadPercent < 100 ? '正在上传文件' : '正在校验并写入整批数据' }}</span>
          <span>已用时 {{ elapsedSeconds }} 秒</span>
        </div>
        <el-progress
          v-if="uploadPercent < 100"
          :percentage="uploadPercent"
          :stroke-width="18"
          text-inside
        />
        <el-progress v-else :percentage="100" :indeterminate="true" :duration="2" :stroke-width="18" />
        <div class="progress-help">整体平移采用整批事务，完成前不会写入一半；页面持续计时表示任务仍在等待结果。</div>
      </div>

      <div v-if="importResult" class="result-section">
        <el-divider /><h3>平移结果</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次编号">{{ importResult.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ importResult.statusName || '完成' }}</el-descriptions-item>
          <el-descriptions-item label="总人数">{{ importResult.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="成功人数"><span class="success">{{ importResult.successCount }}</span></el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Upload, UploadFilled } from '@element-plus/icons-vue'
import { migrateExternalTeam } from '@/api/import'
import { listAgents } from '@/api/agent'

const uploadRef = ref(null)
const loading = ref(false)
const file = ref(null)
const anchorAgentId = ref(null)
const agentOptions = ref([])
const importResult = ref(null)
const uploadPercent = ref(0)
const elapsedSeconds = ref(0)
let elapsedTimer = null

const stopElapsedTimer = () => {
  if (elapsedTimer) window.clearInterval(elapsedTimer)
  elapsedTimer = null
}

const searchAgents = async (keyword) => {
  if (!keyword) return (agentOptions.value = [])
  const res = await listAgents({ keyword, status: 1, pageNum: 1, pageSize: 30 })
  agentOptions.value = res.data?.list || []
}

const downloadTemplate = () => {
  const header = '外部会员编号,手机号,昵称,外部上级编号,初始级别,历史累计有效商品件数,历史个人业绩,历史团队业绩,备注\n'
  const example = 'OLD001,13800000001,示例根成员,,4,150,15000.00,68000.00,示例行请删除\nOLD002,13800000002,示例下级,OLD001,2,10,1000.00,3000.00,\n'
  const blob = new Blob([`\uFEFF${header}${example}`], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '外部团队整体平移模板.csv'
  link.click()
  URL.revokeObjectURL(link.href)
}

const handleChange = (uploadFile) => { file.value = uploadFile.raw; importResult.value = null }
const handleExceed = () => ElMessage.warning('一次只能选择一个文件')

const handleImport = async () => {
  if (!file.value) return ElMessage.warning('请先选择平移文件')
  await ElMessageBox.confirm('确认整批创建会员、代理关系并写入期初历史业绩？历史数据不会产生奖金。', '确认外部团队平移', { type: 'warning', confirmButtonText: '确认平移' })
  loading.value = true
  uploadPercent.value = 0
  elapsedSeconds.value = 0
  stopElapsedTimer()
  elapsedTimer = window.setInterval(() => { elapsedSeconds.value += 1 }, 1000)
  try {
    const res = await migrateExternalTeam(file.value, anchorAgentId.value, (event) => {
      if (event.total) uploadPercent.value = Math.min(100, Math.round(event.loaded * 100 / event.total))
    })
    importResult.value = res.data
    ElMessage.success(`平移完成，共 ${res.data?.successCount || 0} 人`)
    uploadRef.value?.clearFiles()
    file.value = null
  } finally {
    stopElapsedTimer()
    loading.value = false
  }
}

onBeforeUnmount(stopElapsedTimer)
</script>

<style scoped>
.migration-form { margin-top: 22px; }
.help { margin-left: 12px; color: #909399; font-size: 12px; }
.upload-section { margin: 22px 0; }
.result-section { margin-top: 20px; }
.migration-progress { margin: 18px 0; padding: 16px 18px; border: 1px solid #d9ecff; border-radius: 10px; background: #f5faff; }
.progress-title { display: flex; justify-content: space-between; margin-bottom: 10px; font-weight: 600; }
.progress-help { margin-top: 8px; color: #909399; font-size: 13px; }
.success { color: #67c23a; font-weight: 600; }
p { margin: 4px 0; line-height: 1.7; }
</style>
