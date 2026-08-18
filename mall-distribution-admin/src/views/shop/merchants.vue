<template>
  <div class="page-container">
    <div class="page-heading">
      <div><h2>商户管理</h2><p>商户商品按订单结算价快照结算，平台不另收服务费。</p></div>
      <el-button type="primary" @click="open()">新增商户</el-button>
    </div>
    <el-alert title="现有商品默认仍是平台自营。只有商品明确绑定商户后，才会生成商户货款。" type="info" :closable="false" show-icon />
    <div class="toolbar"><el-input v-model="keyword" clearable placeholder="商户名称或编号" @keyup.enter="load" /><el-button type="primary" @click="load">查询</el-button></div>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="merchantNo" label="商户编号" min-width="150" />
      <el-table-column prop="merchantName" label="商户名称" min-width="180" />
      <el-table-column prop="contactName" label="联系人" width="120" />
      <el-table-column prop="contactPhone" label="联系电话" min-width="150" />
      <el-table-column label="结算方式" width="150"><template #default>结算价 × 有效数量</template></el-table-column>
      <el-table-column label="默认结算等待" width="145"><template #default="{ row }"><strong>{{ row.defaultSettlementDays || 0 }} 天</strong></template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="180"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑商户' : '新增商户'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="商户编号"><el-input v-model="form.merchantNo" :disabled="Boolean(form.id)" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="商户名称" required><el-input v-model="form.merchantName" maxlength="128" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="结算等待" required>
          <el-input-number v-model="form.defaultSettlementDays" :min="0" :max="365" :precision="0" style="width:180px" />
          <span class="unit">天</span>
          <div class="field-help">从“客户确认收货与售后入口截止时间中的较晚时点”开始计算；0天表示售后窗口结束后即可结算。</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listMerchants, saveMerchant, updateMerchantStatus } from '@/api/merchant'

const rows = ref([]); const keyword = ref(''); const loading = ref(false); const visible = ref(false); const saving = ref(false)
const emptyForm = () => ({ id: null, merchantNo: '', merchantName: '', contactName: '', contactPhone: '', defaultSettlementDays: 0, status: 1, remark: '' })
const form = ref(emptyForm())
const load = async () => { loading.value = true; try { rows.value = (await listMerchants({ keyword: keyword.value })).data || [] } finally { loading.value = false } }
const open = (row) => { form.value = row ? { ...row } : emptyForm(); visible.value = true }
const submit = async () => { if (!form.value.merchantName?.trim()) return ElMessage.warning('请输入商户名称'); saving.value = true; try { await saveMerchant(form.value.id, form.value); ElMessage.success('商户资料已保存'); visible.value = false; await load() } finally { saving.value = false } }
const toggle = async (row) => { await updateMerchantStatus(row.id, row.status === 1 ? 0 : 1); ElMessage.success('商户状态已更新'); await load() }
onMounted(load)
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.toolbar{display:flex;gap:10px;width:420px;margin:16px 0}.unit{margin-left:8px}.field-help{width:100%;color:#909399;font-size:12px;line-height:1.6;margin-top:6px}
</style>
