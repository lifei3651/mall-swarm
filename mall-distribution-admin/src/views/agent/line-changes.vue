<template>
  <div class="page-container">
    <el-alert
      title="所有拥有移线管理权限的管理员都能查看全部移线记录；提交后立即生效，并保留操作人、原因和前后关系快照。"
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    />
    <div class="toolbar">
      <el-select v-model="status" clearable placeholder="全部状态" @change="load">
        <el-option label="旧版待处理" :value="0"/><el-option label="旧版待生效" :value="1"/>
        <el-option label="已取消/拒绝" :value="2"/><el-option label="已执行" :value="3"/>
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="applyNo" label="申请编号" width="190"/>
      <el-table-column prop="agentId" label="移线会员ID" width="110"/>
      <el-table-column prop="oldParentAgentId" label="原上级" width="100"/>
      <el-table-column prop="newParentAgentId" label="新上级" width="100"/>
      <el-table-column prop="reason" label="移线原因" min-width="180"/>
      <el-table-column prop="applicantName" label="操作人" width="110"/>
      <el-table-column prop="auditorName" label="处理人" width="110"/>
      <el-table-column prop="effectiveTime" label="生效时间" width="170"/>
      <el-table-column label="移线状态" width="120"><template #default="{row}"><el-tag>{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{row}">
          <template v-if="row.status===0 && canManage">
            <el-button link type="success" @click="handleLegacy(row,1)">执行旧申请</el-button>
            <el-button link type="danger" @click="handleLegacy(row,2)">取消旧申请</el-button>
          </template>
          <el-button link type="primary" @click="showSnapshot(row)">查看前后快照</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="snapshotVisible" title="移线关系变化快照" width="760px">
      <el-alert title="历史业绩、历史佣金不转移；移线生效后的新订单按新关系链计算。" type="info" :closable="false" style="margin-bottom: 12px"/>
      <el-divider>移线前</el-divider><pre>{{ selectedBefore }}</pre>
      <el-divider>移线后</el-divider><pre>{{ selectedAfter }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditLineChangeApplication, listLineChangeApplications } from '@/api/agent'
import { useAppStore } from '@/store'
const store=useAppStore()
const rows=ref([]), loading=ref(false), status=ref(null)
const snapshotVisible=ref(false), selectedBefore=ref(''), selectedAfter=ref('')
const canManage=store.hasPermission('line-change:apply')
const statusText=(v)=>['旧版待处理','旧版待生效','已取消/拒绝','已执行'][v]||'未知'
const load=async()=>{loading.value=true;try{const r=await listLineChangeApplications(status.value==null?{}:{status:status.value});rows.value=r.data||[]}finally{loading.value=false}}
const handleLegacy=async(row,result)=>{const action=result===1?'执行':'取消';const {value}=await ElMessageBox.prompt(`请输入${action}旧申请的处理说明`,`确认${action}`,{inputPlaceholder:'处理说明（必填）',inputValidator:v=>!!v?.trim()||'处理说明不能为空'});await auditLineChangeApplication(row.id,{status:result,remark:value.trim()});ElMessage.success(result===1?'旧移线申请已执行':'旧移线申请已取消');await load()}
const pretty=(value)=>{try{return JSON.stringify(JSON.parse(value||'{}'),null,2)}catch{return value||'暂无快照'}}
const showSnapshot=(row)=>{selectedBefore.value=pretty(row.beforeSnapshot);selectedAfter.value=pretty(row.afterSnapshot);snapshotVisible.value=true}
onMounted(load)
</script>

<style scoped>.page-alert{margin-bottom:16px}.toolbar{display:flex;gap:12px;margin-bottom:16px}pre{max-height:280px;overflow:auto;background:#f5f7fa;padding:12px;border-radius:4px;font-size:12px;white-space:pre-wrap;word-break:break-all}</style>
