<template>
  <div class="page-container live-page">
    <div class="page-heading">
      <div><h2>私域直播运营中心</h2><p>平台直接开通指定商城账号。主播获得权限后无需逐场审核，可随时开播；平台可强制停播、暂停或收回权限。</p></div>
      <el-button v-if="activeTab === 'rooms'" type="primary" @click="openRoomDialog()">新建直播间</el-button>
      <el-button v-else type="primary" @click="openAnchorDialog()">开通直播账号</el-button>
    </div>
    <el-alert title="推荐腾讯云标准直播；工厂 24 小时画面也可使用厂家固定 HTTPS 视频源。推流密钥只保存在服务器，数据库和公开接口都不保存。" type="info" :closable="false" show-icon class="page-alert" />
    <el-tabs v-model="activeTab" class="operation-tabs">
      <el-tab-pane label="直播间与营销数据" name="rooms">
        <el-card shadow="never">
          <div class="filters"><el-select v-model="roomFilters.status" clearable placeholder="全部状态" style="width:160px" @change="loadRooms"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-button @click="loadRooms">刷新</el-button></div>
          <el-table v-loading="loadingRooms" :data="rooms" row-key="room.id">
            <el-table-column label="直播间" min-width="300"><template #default="{ row }"><div class="room-summary"><el-image :src="row.room.coverUrl" fit="cover" class="room-cover" /><div><strong>{{ row.room.title }}</strong><span>{{ typeLabel(row.room.liveType) }} · {{ row.room.anchorName || '未分配主播' }}</span><small>{{ row.room.subtitle || '未填写副标题' }}</small></div></div></template></el-table-column>
            <el-table-column label="计划/实际" min-width="185"><template #default="{ row }"><div>{{ formatTime(row.room.scheduledStartTime) }}</div><small class="muted">{{ row.room.actualStartTime ? `实际 ${formatTime(row.room.actualStartTime)}` : '尚未开播' }}</small></template></el-table-column>
            <el-table-column label="观看/商品" width="120"><template #default="{ row }"><div>{{ row.room.viewerCount || 0 }} 人在线</div><small class="muted">{{ row.products?.length || 0 }} 件商品</small></template></el-table-column>
            <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="stateType(row.roomState)">{{ stateLabel(row.roomState) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="270" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openAnalytics(row)">营销数据</el-button><el-button link type="primary" @click="openRoomDialog(row)">编辑</el-button><el-button v-if="Number(row.room.status) === 2" link type="danger" @click="forceStop(row)">强制停播</el-button><el-dropdown v-else trigger="click" @command="(status) => changeRoomStatus(row,status)"><el-button link type="primary">切换状态</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in manualStatusOptions" :key="item.value" :command="item.value" :disabled="Number(row.room.status) === item.value">{{ item.label }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
          </el-table>
          <el-empty v-if="!loadingRooms && !rooms.length" description="还没有直播间" />
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="直播账号与权限" name="anchors">
        <el-card shadow="never">
          <div class="filters"><el-select v-model="anchorFilters.status" clearable placeholder="全部权限状态" style="width:160px" @change="loadAnchors"><el-option label="可开播" :value="1" /><el-option label="已暂停" :value="2" /><el-option label="已收回" :value="3" /></el-select><el-button @click="loadAnchors">刷新</el-button></div>
          <el-table v-loading="loadingAnchors" :data="anchors" row-key="anchor.id">
            <el-table-column label="直播账号" min-width="250"><template #default="{ row }"><strong>{{ row.anchor.displayName }}</strong><div class="muted">商城账号：{{ row.memberAccount }}</div></template></el-table-column>
            <el-table-column label="类型/机构" min-width="210"><template #default="{ row }"><div>{{ typeLabel(row.anchor.anchorType) }}</div><small class="muted">{{ row.anchor.companyName || '平台账号' }}</small></template></el-table-column>
            <el-table-column label="直播间" width="130"><template #default="{ row }">{{ row.liveRoomCount || 0 }} 个<span v-if="row.liveRoomLiveCount" class="live-count">{{ row.liveRoomLiveCount }} 个直播中</span></template></el-table-column>
            <el-table-column label="权限" width="100"><template #default="{ row }"><el-tag :type="Number(row.anchor.status) === 1 ? 'success' : Number(row.anchor.status) === 2 ? 'warning' : 'danger'">{{ row.statusLabel }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="260" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openAnchorDialog(row)">编辑</el-button><el-button v-if="Number(row.anchor.status) !== 1" link type="success" @click="changeAnchorStatus(row,1)">恢复权限</el-button><el-button v-if="Number(row.anchor.status) === 1" link type="warning" @click="changeAnchorStatus(row,2)">暂停</el-button><el-button v-if="Number(row.anchor.status) !== 3" link type="danger" @click="changeAnchorStatus(row,3)">收回权限</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!loadingAnchors && !anchors.length" description="还没有开通直播账号" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="roomDialogVisible" :title="roomForm.id ? '编辑直播间' : '新建直播间'" width="780px" destroy-on-close>
      <el-form ref="roomFormRef" :model="roomForm" :rules="roomRules" label-width="112px">
        <el-form-item label="直播封面" prop="coverUrl"><el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadCover"><el-image v-if="roomForm.coverUrl" :src="roomForm.coverUrl" fit="cover" class="cover-preview" /><div v-else class="cover-upload">点击上传封面</div></el-upload><span class="field-help">建议 16:9 横图。</span></el-form-item>
        <el-form-item label="直播间标题" prop="title"><el-input v-model="roomForm.title" maxlength="80" show-word-limit /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="roomForm.subtitle" maxlength="160" show-word-limit /></el-form-item>
        <el-row :gutter="14"><el-col :span="12"><el-form-item label="直播类型"><el-select v-model="roomForm.liveType" style="width:100%"><el-option label="厂家商品直播" value="PRODUCT" /><el-option label="平台讲解直播" value="PLATFORM" /><el-option label="工厂实景直播" value="FACTORY" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="授权主播" prop="anchorId"><el-select v-model="roomForm.anchorId" filterable style="width:100%" placeholder="选择可开播账号"><el-option v-for="item in activeAnchors" :key="item.anchor.id" :label="`${item.anchor.displayName}（${item.memberAccount}）`" :value="item.anchor.id" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="关联商品" prop="productIds"><el-select v-model="roomForm.productIds" multiple filterable collapse-tags :max-collapse-tags="3" style="width:100%" placeholder="最多选择20个在售商品"><el-option v-for="item in products" :key="item.id" :label="`${item.productName}（¥${item.salePrice}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="计划时间" required><el-date-picker v-model="timeRange" type="datetimerange" start-placeholder="计划开播" end-placeholder="计划结束" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="视频服务"><el-radio-group v-model="roomForm.providerCode"><el-radio-button value="TENCENT">腾讯云直播</el-radio-button><el-radio-button value="EXTERNAL">厂家固定视频源</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="roomForm.providerCode === 'EXTERNAL'" label="观看/回放地址"><el-input v-model="roomForm.watchUrl" maxlength="2048" placeholder="https://...（建议 HLS .m3u8）" /><span class="field-help">工厂摄像头或厂家平台提供的 HTTPS 地址。不得填写内网地址或推流密钥。</span></el-form-item>
        <el-row :gutter="14"><el-col :span="8"><el-form-item label="允许评论"><el-switch v-model="roomForm.commentEnabled" :active-value="1" :inactive-value="0" /></el-form-item></el-col><el-col :span="8"><el-form-item label="允许分享"><el-switch v-model="roomForm.shareEnabled" :active-value="1" :inactive-value="0" /></el-form-item></el-col><el-col :span="8"><el-form-item label="展示顺序"><el-input-number v-model="roomForm.sortOrder" :min="-9999" :max="9999" /></el-form-item></el-col></el-row>
        <el-form-item label="保存后状态"><el-radio-group v-model="roomForm.status"><el-radio-button value="0">草稿</el-radio-button><el-radio-button value="1">发布预告</el-radio-button><el-radio-button v-if="Number(roomForm.status) === 2" value="2" disabled>直播中</el-radio-button><el-radio-button value="3">已结束</el-radio-button><el-radio-button value="4">停用</el-radio-button></el-radio-group><span class="field-help">“直播中”只能由已授权主播开始直播后进入，避免后台误操作伪造直播状态。</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="roomDialogVisible=false">取消</el-button><el-button type="primary" :loading="savingRoom" @click="submitRoom">保存直播间</el-button></template>
    </el-dialog>

    <el-dialog v-model="anchorDialogVisible" :title="anchorForm.id ? '编辑直播账号' : '开通直播账号'" width="620px" destroy-on-close>
      <el-form ref="anchorFormRef" :model="anchorForm" :rules="anchorRules" label-width="122px">
        <el-form-item label="商城登录账号" prop="memberAccount"><el-input v-model="anchorForm.memberAccount" :disabled="Boolean(anchorForm.id)" maxlength="64" placeholder="手机号或登录账号" /><span class="field-help">必须先注册商城账号；平台添加后立即获得开播权限，不需要逐场审核。</span></el-form-item>
        <el-form-item label="主播展示名称" prop="displayName"><el-input v-model="anchorForm.displayName" maxlength="60" /></el-form-item>
        <el-form-item label="账号类型" prop="anchorType"><el-select v-model="anchorForm.anchorType" style="width:100%"><el-option label="厂家商品主播" value="PRODUCT" /><el-option label="平台讲解主播" value="PLATFORM" /><el-option label="工厂实景账号" value="FACTORY" /></el-select></el-form-item>
        <el-form-item label="厂家/机构名称"><el-input v-model="anchorForm.companyName" maxlength="120" /></el-form-item>
        <el-form-item label="主播简介"><el-input v-model="anchorForm.bio" type="textarea" :rows="3" maxlength="300" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="anchorDialogVisible=false">取消</el-button><el-button type="primary" :loading="savingAnchor" @click="submitAnchor">保存并开通</el-button></template>
    </el-dialog>

    <el-dialog v-model="analyticsVisible" title="直播营销数据" width="720px">
      <div v-loading="analyticsLoading" class="analytics-grid"><article><span>累计观看人数</span><strong>{{ analytics.uniqueViewers || 0 }}</strong></article><article><span>当前在线</span><strong>{{ analytics.currentViewers || 0 }}</strong></article><article><span>平均停留</span><strong>{{ durationLabel(analytics.averageDurationSeconds) }}</strong></article><article><span>分享次数</span><strong>{{ analytics.shareCount || 0 }}</strong></article><article><span>商品点击</span><strong>{{ analytics.productClickCount || 0 }}</strong><small>观看→点击 {{ analytics.viewerToClickRate || 0 }}%</small></article><article><span>已支付订单</span><strong>{{ analytics.paidOrderCount || 0 }}</strong><small>点击→支付 {{ analytics.clickToPaidRate || 0 }}%</small></article><article class="wide"><span>直播成交额</span><strong>¥{{ analytics.paidAmount || 0 }}</strong></article><article class="wide"><span>有效评论</span><strong>{{ analytics.commentCount || 0 }}</strong></article></div>
      <div class="comment-audit"><h3>评论管理</h3><el-table :data="adminComments" max-height="260"><el-table-column label="用户" prop="displayName" width="110" /><el-table-column label="评论" prop="content" min-width="280" /><el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="Number(row.status)===1?'success':'info'">{{ Number(row.status)===1?'公开':'已隐藏' }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button link :type="Number(row.status)===1?'danger':'primary'" @click="toggleComment(row)">{{ Number(row.status)===1?'隐藏':'恢复' }}</el-button></template></el-table-column></el-table></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { forceStopLiveRoom, getLiveRoomAnalytics, listAdminLiveComments, listLiveAnchors, listLiveRooms, listShopProducts, saveLiveAnchor, saveLiveRoom, updateLiveAnchorStatus, updateLiveCommentStatus, updateLiveRoomStatus, uploadShopImage } from '@/api/shop'

const statusOptions=[{value:0,label:'草稿'},{value:1,label:'预告'},{value:2,label:'直播中'},{value:3,label:'已结束'},{value:4,label:'停用'}]
const manualStatusOptions=statusOptions.filter(item=>item.value!==2)
const stateLabel=(state)=>({DRAFT:'草稿',UPCOMING:'预告',CONNECTING:'视频连接中',LIVE:'直播中',ENDED:'已结束',DISABLED:'停用'}[state]||state)
const stateType=(state)=>({LIVE:'danger',CONNECTING:'warning',UPCOMING:'success',ENDED:'info',DRAFT:'warning',DISABLED:'info'}[state]||'info')
const typeLabel=(type)=>({PRODUCT:'厂家商品直播',PLATFORM:'平台讲解直播',FACTORY:'工厂实景直播'}[type]||'平台直播')
const formatTime=(value)=>value?String(value).replace('T',' ').slice(0,16):'-'
const durationLabel=(seconds)=>{const value=Number(seconds||0);return value>=60?`${Math.floor(value/60)}分${value%60}秒`:`${value}秒`}
const roomDefaults=()=>({id:null,title:'',subtitle:'',coverUrl:'',anchorId:null,liveType:'PRODUCT',providerCode:'TENCENT',watchUrl:'',commentEnabled:1,shareEnabled:1,status:0,heatCount:0,sortOrder:0,productIds:[]})
const anchorDefaults=()=>({id:null,memberAccount:'',displayName:'',anchorType:'PRODUCT',companyName:'',bio:''})
const activeTab=ref('rooms'),rooms=ref([]),anchors=ref([]),products=ref([]),loadingRooms=ref(false),loadingAnchors=ref(false)
const roomFilters=ref({status:null}),anchorFilters=ref({status:null}),roomDialogVisible=ref(false),anchorDialogVisible=ref(false),analyticsVisible=ref(false)
const savingRoom=ref(false),savingAnchor=ref(false),analyticsLoading=ref(false),roomFormRef=ref(null),anchorFormRef=ref(null)
const roomForm=ref(roomDefaults()),anchorForm=ref(anchorDefaults()),timeRange=ref([]),analytics=ref({}),adminComments=ref([]),analyticsRoomId=ref(null)
const activeAnchors=computed(()=>anchors.value.filter(item=>Number(item.anchor.status)===1))
const roomRules={title:[{required:true,message:'请输入直播间标题',trigger:'blur'}],coverUrl:[{required:true,message:'请上传直播封面',trigger:'change'}],anchorId:[{required:true,message:'请选择已授权主播',trigger:'change'}]}
const anchorRules={memberAccount:[{required:true,message:'请输入商城登录账号',trigger:'blur'}],displayName:[{required:true,message:'请输入主播展示名称',trigger:'blur'}],anchorType:[{required:true,message:'请选择账号类型',trigger:'change'}]}
const loadRooms=async()=>{loadingRooms.value=true;try{rooms.value=(await listLiveRooms({status:roomFilters.value.status})).data||[]}finally{loadingRooms.value=false}}
const loadAnchors=async()=>{loadingAnchors.value=true;try{anchors.value=(await listLiveAnchors({status:anchorFilters.value.status})).data||[]}finally{loadingAnchors.value=false}}
const loadProducts=async()=>{products.value=(await listShopProducts({status:1,pageNum:1,pageSize:100})).data?.list||[]}
const openRoomDialog=(row)=>{roomForm.value=row?{...roomDefaults(),...row.room,status:Number(row.room.status),productIds:[...(row.productIds||[])]}:roomDefaults();timeRange.value=row?[row.room.scheduledStartTime,row.room.scheduledEndTime].filter(Boolean):[];roomDialogVisible.value=true}
const openAnchorDialog=(row)=>{anchorForm.value=row?{...anchorDefaults(),...row.anchor,memberAccount:row.memberAccount}:anchorDefaults();anchorDialogVisible.value=true}
const uploadCover=async({file})=>{roomForm.value.coverUrl=(await uploadShopImage(file)).data;ElMessage.success('封面已上传')}
const submitRoom=async()=>{await roomFormRef.value?.validate();if(!timeRange.value.length)return ElMessage.warning('请选择计划开播时间');if(!roomForm.value.productIds.length)return ElMessage.warning('至少关联一个在售商品');savingRoom.value=true;try{await saveLiveRoom(roomForm.value.id,{...roomForm.value,status:Number(roomForm.value.status),scheduledStartTime:timeRange.value[0],scheduledEndTime:timeRange.value[1]||null});ElMessage.success('直播间已保存');roomDialogVisible.value=false;await loadRooms()}finally{savingRoom.value=false}}
const submitAnchor=async()=>{await anchorFormRef.value?.validate();savingAnchor.value=true;try{await saveLiveAnchor(anchorForm.value.id,anchorForm.value);ElMessage.success(anchorForm.value.id?'直播账号已更新':'直播账号已开通');anchorDialogVisible.value=false;await loadAnchors()}finally{savingAnchor.value=false}}
const changeRoomStatus=async(row,status)=>{try{await ElMessageBox.confirm(`确定将“${row.room.title}”切换为“${statusOptions.find(item=>item.value===Number(status))?.label}”吗？`,'切换直播状态',{type:'warning'})}catch{return}await updateLiveRoomStatus(row.room.id,status);ElMessage.success('直播状态已更新');await loadRooms()}
const forceStop=async(row)=>{let result;try{result=await ElMessageBox.prompt('请输入强制停播原因，原因会写入操作日志。','平台强制停播',{inputPattern:/^.{2,200}$/,inputErrorMessage:'请输入2到200个字',type:'warning'})}catch{return}await forceStopLiveRoom(row.room.id,result.value);ElMessage.success('直播已强制停止');await loadRooms()}
const changeAnchorStatus=async(row,status)=>{const action=status===1?'恢复':status===2?'暂停':'收回';try{await ElMessageBox.confirm(`${action}“${row.anchor.displayName}”的直播权限吗？${status===1?'恢复后可立即开播。':'正在直播的房间会立即停播。'}`,'直播权限变更',{type:'warning'})}catch{return}await updateLiveAnchorStatus(row.anchor.id,status);ElMessage.success(`直播权限已${action}`);await Promise.all([loadAnchors(),loadRooms()])}
const openAnalytics=async(row)=>{analyticsVisible.value=true;analyticsLoading.value=true;analytics.value={};adminComments.value=[];analyticsRoomId.value=row.room.id;try{const [stats,comments]=await Promise.all([getLiveRoomAnalytics(row.room.id),listAdminLiveComments(row.room.id,{limit:100})]);analytics.value=stats.data||{};adminComments.value=comments.data||[]}finally{analyticsLoading.value=false}}
const toggleComment=async(row)=>{await updateLiveCommentStatus(row.id,Number(row.status)===1?2:1);const comments=await listAdminLiveComments(analyticsRoomId.value,{limit:100});adminComments.value=comments.data||[];ElMessage.success(Number(row.status)===1?'评论已隐藏':'评论已恢复')}
onMounted(async()=>{await Promise.all([loadRooms(),loadAnchors(),loadProducts()])})
</script>

<style scoped>
.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:16px}.page-heading h2{margin:0;color:#303133;font-size:22px}.page-heading p{max-width:820px;margin:7px 0 0;color:#909399;font-size:13px;line-height:1.6}.page-alert{margin-bottom:12px}.operation-tabs{--el-tabs-header-height:48px}.filters{display:flex;gap:10px;margin-bottom:16px}.room-summary{display:flex;align-items:center;gap:12px;min-width:0}.room-cover{width:104px;height:60px;flex:0 0 auto;border-radius:8px;background:#f2f3f5}.room-summary>div{min-width:0}.room-summary strong,.room-summary span,.room-summary small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.room-summary span,.room-summary small,.muted,.field-help{color:#909399;font-size:12px}.room-summary span{margin-top:5px}.room-summary small{margin-top:3px}.live-count{display:block;color:#ef1742;font-size:11px}.cover-preview,.cover-upload{width:240px;height:135px;border-radius:10px}.cover-preview{display:block}.cover-upload{display:grid;place-items:center;color:#909399;background:#f6f7f9;border:1px dashed #c8cdd5}.field-help{display:block;width:100%;margin-top:6px;line-height:1.5}.analytics-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;min-height:180px}.analytics-grid article{padding:16px;background:#f6f8fb;border:1px solid #e8edf3;border-radius:12px}.analytics-grid span,.analytics-grid strong,.analytics-grid small{display:block}.analytics-grid span{color:#667085;font-size:12px}.analytics-grid strong{margin-top:8px;color:#101828;font-size:24px}.analytics-grid small{margin-top:5px;color:#0a7c50}.analytics-grid .wide{grid-column:span 1}@media(max-width:900px){.page-heading{flex-direction:column}.page-heading .el-button{width:100%}.analytics-grid{grid-template-columns:repeat(2,1fr)}}
.comment-audit{margin-top:20px}.comment-audit h3{margin:0 0 10px;font-size:16px}
</style>
