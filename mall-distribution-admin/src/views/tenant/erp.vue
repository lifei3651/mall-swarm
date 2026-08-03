<template>
  <div class="page-container">
    <el-alert title="配置完成并启用后，商城支付订单会自动入ERP推单队列。密钥仅可写入，页面不会回显。" type="info" :closable="false" class="mb-16" />
    <el-button type="primary" @click="openDialog()">新增/配置 ERP</el-button>
    <el-table :data="integrations" v-loading="loading" class="mt-16">
      <el-table-column prop="providerCode" label="ERP服务商" width="130"><template #default="{ row }">{{ providerName(row.providerCode) }}</template></el-table-column>
      <el-table-column prop="integrationName" label="配置名称" width="180" />
      <el-table-column prop="environment" label="运行环境" width="100" />
      <el-table-column prop="enabled" label="接口状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '未启用' }}</el-tag></template></el-table-column>
      <el-table-column prop="endpoint" label="接口地址" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openDialog(row)">编辑</el-button></template></el-table-column>
    </el-table>
    <el-card class="mt-16"><template #header>ERP 推送任务</template><el-table :data="tasks"><el-table-column prop="taskNo" label="推送任务编号" width="220"/><el-table-column prop="providerCode" label="ERP服务商" width="110"/><el-table-column prop="bizId" label="商城订单ID" width="180"/><el-table-column prop="status" label="推送状态" width="110"><template #default="{ row }">{{ ['待推送','成功','失败待重试','已终止'][row.status] }}</template></el-table-column><el-table-column prop="lastError" label="最近错误信息"/><el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="row.status !== 1" link type="primary" @click="retry(row)">重试</el-button></template></el-table-column></el-table></el-card>
    <el-dialog v-model="visible" title="ERP 配置" width="620px"><el-form :model="form" label-width="110px"><el-form-item label="ERP厂商"><el-select v-model="form.providerCode" :disabled="!!form.id"><el-option label="聚水潭" value="JUSHUITAN"/><el-option label="旺店通" value="WANGDIAN"/><el-option label="金蝶" value="KINGDEE"/></el-select></el-form-item><el-form-item label="配置名称"><el-input v-model="form.integrationName"/></el-form-item><el-form-item label="环境"><el-radio-group v-model="form.environment"><el-radio value="TEST">测试</el-radio><el-radio value="PROD">正式</el-radio></el-radio-group></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0"/></el-form-item><el-form-item label="接口地址"><el-input v-model="form.endpoint" placeholder="由ERP开放平台提供"/></el-form-item><el-form-item label="App Key"><el-input v-model="form.appKey"/></el-form-item><el-form-item label="App Secret"><el-input v-model="form.appSecret" type="password" show-password placeholder="留空则保持不变"/></el-form-item><el-form-item label="回调令牌"><el-input v-model="form.callbackToken" type="password" show-password placeholder="留空则保持不变"/></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listErpIntegrations, saveErpIntegration, listErpTasks, retryErpTask } from '@/api/erp'
const integrations=ref([]), tasks=ref([]), loading=ref(false), visible=ref(false)
const empty=()=>({ tenantId:1, providerCode:'JUSHUITAN', integrationName:'', enabled:0, environment:'TEST', endpoint:'', appKey:'', appSecret:'', callbackToken:'', remark:'' })
const form=ref(empty())
const providerName=(v)=>({JUSHUITAN:'聚水潭',WANGDIAN:'旺店通',KINGDEE:'金蝶'}[v]||v)
async function load(){ loading.value=true; try { integrations.value=(await listErpIntegrations()).data||[]; tasks.value=(await listErpTasks({})).data||[] } finally { loading.value=false } }
function openDialog(row){ form.value=row?{...row,appSecret:'',callbackToken:''}:empty(); visible.value=true }
async function save(){ await saveErpIntegration(form.value); ElMessage.success('ERP配置已保存'); visible.value=false; load() }
async function retry(row){ const ok=(await retryErpTask(row.id)).data; ElMessage[ok?'success':'warning'](ok?'推送成功':'推送未成功，请检查配置'); load() }
onMounted(load)
</script>
<style scoped>.mt-16{margin-top:16px}.mb-16{margin-bottom:16px}</style>
