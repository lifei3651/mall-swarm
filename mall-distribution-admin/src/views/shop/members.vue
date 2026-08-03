<template>
  <div class="page-container">
    <el-alert
      title="本页统一管理全部商城账号。未进入体系的账号可直接设定1–8级；完成首单或后台设级后，会在同一行显示卡级、业绩和奖金账户。"
      type="info"
      :closable="false"
      show-icon
      class="account-tip"
    />
    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="登录账号/手机号/昵称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="账号状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px" @change="handleSearch">
            <el-option label="账号启用" :value="1" />
            <el-option label="账号禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员身份">
          <el-select v-model="query.promotionActivated" clearable placeholder="全部" style="width: 140px" @change="handleSearch">
            <el-option label="已进入奖金体系" :value="1" />
            <el-option label="未进入奖金体系" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="卡级">
          <el-select v-model="query.agentLevel" clearable placeholder="全部" style="width: 130px" @change="handleSearch">
            <el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新增商城账号</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="members" v-loading="loading" :empty-text="memberEmptyText" style="width: 100%">
      <el-table-column prop="username" label="登录账号" min-width="145"><template #default="{ row }">{{ row.username || '—' }}</template></el-table-column>
      <el-table-column label="会员名称" min-width="195">
        <template #default="{ row }">
          <div class="member-name">{{ memberDisplayName(row) }}</div>
          <div v-if="memberNameHint(row)" class="sub">{{ memberNameHint(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="登录手机号" width="140"><template #default="{ row }">{{ row.phone || '—' }}</template></el-table-column>
      <el-table-column label="直属邀请人" min-width="175">
        <template #default="{ row }">
          <template v-if="row.inviterUserId">
            <div class="member-name">{{ inviterDisplayName(row) }}</div>
            <div class="sub">{{ inviterIdentityText(row) }}</div>
          </template>
          <span v-else class="sub">创始会员 / 无邀请人</span>
        </template>
      </el-table-column>
      <el-table-column label="会员卡级" width="105">
        <template #default="{ row }">
          <template v-if="row.promotionActivated">
            <el-tag :type="row.agentStatus === 1 ? 'primary' : 'danger'">{{ row.agentLevelName || levelName(row.agentLevel) }}</el-tag>
            <div v-if="row.agentStatus !== 1" class="sub">推广已停用</div>
          </template>
          <el-tag v-else type="info">未进入体系</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前余额" width="120" align="right">
        <template #default="{ row }">¥{{ money(row.availableBalance) }}</template>
      </el-table-column>
      <el-table-column label="待结算奖金" width="120" align="right">
        <template #default="{ row }">¥{{ money(row.unsettledCommission) }}</template>
      </el-table-column>
      <el-table-column label="团队总业绩" width="125" align="right">
        <template #default="{ row }">¥{{ money(row.teamPerformance) }}</template>
      </el-table-column>
      <el-table-column label="账号状态" width="105">
        <template #default="{ row }">
          <el-tag :type="accountStatusTag(row)">{{ accountStatusName(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最近登录时间" width="180"><template #default="{ row }">{{ row.lastLoginTime || '—' }}</template></el-table-column>
      <el-table-column label="操作" fixed="right" width="210">
        <template #default="{ row }">
          <div class="operation-actions">
            <el-button type="primary" link @click="openProfile(row, 'view')">查看</el-button>
            <el-button type="success" link @click="openProfile(row, 'edit')">编辑</el-button>
            <el-button :type="row.status === 1 ? 'warning' : 'success'" link @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination-container"
      background
      layout="total, prev, pager, next, sizes"
      :total="pagination.total"
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.size"
      @current-change="fetchMembers"
      @size-change="fetchMembers"
    />

    <el-dialog v-model="profileVisible" :title="profileDialogTitle" width="1180px" destroy-on-close>
      <div v-loading="profileLoading">
        <div v-if="isEditMode" class="profile-actions-panel">
          <div class="profile-actions-heading">
            <strong>会员管理操作</strong>
            <span>除启用/禁用外，会员相关操作统一在这里处理</span>
          </div>
          <div class="profile-actions-buttons">
            <el-dropdown trigger="click" @command="(command) => openAccountSecurity(currentMember, command)">
              <el-button>账号安全</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="PHONE">修改登录手机号</el-dropdown-item>
                  <el-dropdown-item command="PASSWORD">重置登录密码</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-if="currentMember.loginLocked" type="success" @click="unlockMember(currentMember)">解除登录锁定</el-button>
            <el-button v-if="canManageDistribution" type="primary" @click="openLevelAdjust(currentMember)">
              {{ currentMember.promotionActivated ? '调整卡级' : '设定级别' }}
            </el-button>
            <el-button v-if="currentMember.promotionActivated && canManageDistribution" type="success" @click="openTeam(currentMember)">查看团队</el-button>
            <el-dropdown v-if="currentMember.promotionActivated && canManageAssets" trigger="click" @command="(command) => openAssetAdjust(currentMember, command)">
              <el-button type="primary">调整余额</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="ISSUE">增加余额</el-dropdown-item>
                  <el-dropdown-item command="DEDUCT">扣减余额</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-tooltip
              v-if="currentMember.promotionActivated && canApplyLineChange"
              content="该会员有待移线处理申请，暂不可再进行移线操作"
              placement="top"
              :disabled="!currentMember.hasPendingLineChange"
            >
              <span class="action-tooltip-trigger">
                <el-button type="warning" :disabled="currentMember.hasPendingLineChange" @click="openSwitchLine(currentMember)">会员移线</el-button>
              </span>
            </el-tooltip>
          </div>
        </div>

        <el-tabs v-model="profileTab" class="profile-tabs">
          <el-tab-pane label="资料、订单与地址" name="overview">
            <el-descriptions :column="4" border>
              <el-descriptions-item label="登录账号">{{ currentMember.username || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ profile.member?.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="昵称">{{ profile.member?.nickname || '-' }}</el-descriptions-item>
              <el-descriptions-item label="直属邀请人">{{ currentMember.inviterName || (currentMember.inviterUserId ? '未知会员' : '无') }}</el-descriptions-item>
              <el-descriptions-item label="邀请人登录账号">{{ currentMember.inviterMemberAccount || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邀请人手机号">{{ currentMember.inviterPhone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="账号状态">{{ accountStatusName(currentMember) }}</el-descriptions-item>
              <el-descriptions-item label="推广资格">{{ currentMember.promotionActivated ? '已进入奖金体系' : '尚未进入奖金体系' }}</el-descriptions-item>
              <el-descriptions-item label="当前卡级">{{ currentMember.promotionActivated ? levelName(currentMember.agentLevel) : '-' }}</el-descriptions-item>
              <el-descriptions-item label="推广线上级">{{ currentMember.parentName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="余额">¥{{ money(balanceOf(profile.assetAccounts)) }}</el-descriptions-item>
              <el-descriptions-item label="待结算奖金">¥{{ money(profile.account?.unsettledCommission) }}</el-descriptions-item>
              <el-descriptions-item label="累计有效件数">{{ currentMember.totalOrders || 0 }}</el-descriptions-item>
              <el-descriptions-item label="团队业绩">{{ money(profile.performance?.teamPerformance) }}</el-descriptions-item>
            </el-descriptions>

            <el-card v-if="profile.migrationBaseline" class="block migration-card" shadow="never">
              <template #header>外部团队平移期初数据</template>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="原平台会员编号">{{ profile.migrationBaseline.externalMemberCode }}</el-descriptions-item>
                <el-descriptions-item label="平移批次">{{ profile.migrationBaseline.batchNo }}</el-descriptions-item>
                <el-descriptions-item label="切换时间">{{ profile.migrationBaseline.cutoverTime }}</el-descriptions-item>
                <el-descriptions-item label="迁入卡级">{{ levelName(profile.migrationBaseline.initialLevel) }}</el-descriptions-item>
                <el-descriptions-item label="历史有效商品件数">{{ profile.migrationBaseline.historicalOrderCount }}</el-descriptions-item>
                <el-descriptions-item label="历史个人业绩">{{ money(profile.migrationBaseline.historicalPersonalPerformance) }}</el-descriptions-item>
                <el-descriptions-item label="历史团队业绩">{{ money(profile.migrationBaseline.historicalTeamPerformance) }}</el-descriptions-item>
              </el-descriptions>
              <el-alert class="baseline-note" title="以上是原平台带入的期初基线，不补发历史奖金；切换后的新订单按当前规则计算。" type="info" :closable="false" />
            </el-card>

            <el-card class="block" shadow="never">
              <template #header>收货地址</template>
              <el-table :data="profile.addresses || []" size="small" style="width: 100%" empty-text="该会员暂未添加收货地址">
                <el-table-column prop="receiverName" label="收货人" width="120" />
                <el-table-column prop="receiverPhone" label="手机号" width="140" />
                <el-table-column label="完整收货地址" min-width="260"><template #default="{ row }">{{ joinAddress(row) || '—' }}</template></el-table-column>
                <el-table-column prop="isDefault" label="是否默认地址" width="110"><template #default="{ row }">{{ row.isDefault === 1 ? '是' : '否' }}</template></el-table-column>
              </el-table>
            </el-card>

            <el-card class="block" shadow="never">
              <template #header>订单</template>
              <el-table :data="profile.orders || []" size="small" style="width: 100%" empty-text="该会员暂无订单">
                <el-table-column label="订单编号" min-width="180"><template #default="{ row }">{{ row.order?.orderNo || '—' }}</template></el-table-column>
                <el-table-column label="订单实付金额" width="125"><template #default="{ row }">¥{{ money(row.order?.payAmount) }}</template></el-table-column>
                <el-table-column label="订单状态" width="100"><template #default="{ row }"><el-tag :type="orderTag(row.order?.status)">{{ orderStatus(row.order?.status) }}</el-tag></template></el-table-column>
                <el-table-column label="商品明细" min-width="220"><template #default="{ row }">{{ (row.items || []).map((item) => `${item.productName} x${item.quantity}`).join('，') || '—' }}</template></el-table-column>
                <el-table-column label="售后申请数" width="110"><template #default="{ row }">{{ row.afterSales?.length || 0 }}</template></el-table-column>
              </el-table>
            </el-card>
          </el-tab-pane>

          <el-tab-pane v-if="currentMember.promotionActivated && canReadFinance" label="账务全景" name="finance">
            <div class="profile-finance-metrics">
              <div><span>累计奖金</span><strong>¥{{ money(financeProfile.account?.totalCommission) }}</strong></div>
              <div><span>待结算</span><strong class="warning-text">¥{{ money(financeProfile.account?.unsettledCommission) }}</strong></div>
              <div><span>当前余额</span><strong class="success-text">¥{{ money(financeBalance) }}</strong></div>
              <div><span>未清欠款</span><strong :class="{ 'danger-text': Number(financeProfile.pendingDebtAmount || 0) > 0 }">¥{{ money(financeProfile.pendingDebtAmount) }}</strong></div>
            </div>

            <el-card class="block" shadow="never">
              <template #header>奖金记录</template>
              <el-table :data="financeProfile.commissions || []" size="small" style="width:100%" empty-text="该会员暂无奖金记录">
                <el-table-column prop="orderNo" label="来源订单编号" min-width="180" />
                <el-table-column prop="orderMemberAccount" label="下单会员账号" width="145" />
                <el-table-column label="奖金类型" width="170"><template #default="{ row }">{{ bonusTypeText(row) }}</template></el-table-column>
                <el-table-column label="奖金比例" width="90"><template #default="{ row }">{{ percent(row.commissionRate) }}</template></el-table-column>
                <el-table-column label="奖金金额" width="110"><template #default="{ row }">¥{{ money(row.commissionAmount) }}</template></el-table-column>
                <el-table-column prop="statusName" label="奖金状态" width="100" />
                <el-table-column prop="createTime" label="奖金产生时间" width="170" />
              </el-table>
            </el-card>

            <el-card class="block" shadow="never">
              <template #header>余额流水</template>
              <el-table :data="financeAssetFlows" size="small" style="width:100%" empty-text="该会员暂无余额流水">
                <el-table-column prop="flowNo" label="流水号" min-width="185" />
                <el-table-column label="余额变动" width="110"><template #default="{ row }"><span :class="isIncome(row.changeType) ? 'success-text' : 'danger-text'">{{ isIncome(row.changeType) ? '+' : '-' }}¥{{ money(row.amount) }}</span></template></el-table-column>
                <el-table-column label="资金来源" min-width="170"><template #default="{ row }">{{ assetFlowSourceText(row) }}</template></el-table-column>
                <el-table-column label="变动后余额" width="125"><template #default="{ row }">¥{{ money(row.balanceAfter) }}</template></el-table-column>
                <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
                <el-table-column prop="createTime" label="变动时间" width="170" />
              </el-table>
            </el-card>

            <el-card class="block" shadow="never">
              <template #header>退款追回与欠款抵扣</template>
              <el-table :data="financeProfile.clawbacks || []" size="small" style="width:100%" empty-text="该会员暂无退款追回记录">
                <el-table-column prop="orderNo" label="订单编号" min-width="180" />
                <el-table-column label="应追回金额" width="120"><template #default="{ row }">¥{{ money(row.clawbackAmount) }}</template></el-table-column>
                <el-table-column label="已扣回金额" width="120"><template #default="{ row }">¥{{ money(row.deductedAmount) }}</template></el-table-column>
                <el-table-column label="未清欠款" width="120"><template #default="{ row }">¥{{ money(row.debtAmount) }}</template></el-table-column>
                <el-table-column prop="reason" label="追回原因" min-width="220" show-overflow-tooltip />
              </el-table>
            </el-card>

            <el-card class="block" shadow="never">
              <template #header>提现记录</template>
              <el-table :data="financeProfile.withdraws || []" size="small" style="width:100%" empty-text="该会员暂无提现记录">
                <el-table-column prop="withdrawNo" label="提现单号" min-width="180" />
                <el-table-column label="提现金额" width="110"><template #default="{ row }">¥{{ money(row.withdrawAmount) }}</template></el-table-column>
                <el-table-column prop="accountName" label="账户姓名" width="120" />
                <el-table-column prop="status" label="提现状态" width="100" />
                <el-table-column prop="createTime" label="申请时间" width="170" />
                <el-table-column prop="auditRemark" label="审核备注" min-width="180" />
              </el-table>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <el-dialog v-model="levelVisible" :title="levelForm.promotionActivated ? '会员手工调级' : '开通会员并设定卡级'" width="540px" destroy-on-close>
      <el-alert
        :title="levelForm.promotionActivated
          ? '提交后立即升/降级并写入变更日志。历史订单、历史业绩和历史奖金不重算，新级别影响之后产生的订单。'
          : '提交后立即创建推广身份、奖金账户和上下级关系，并按所选卡级生效；不补发开通前的历史奖金。'"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form :model="levelForm" label-width="110px" class="level-form">
        <el-form-item label="商城账号">
          <el-input :model-value="`${levelForm.memberName}（${levelForm.memberAccount}）`" disabled />
        </el-form-item>
        <el-form-item label="当前卡级">
          <el-input :model-value="levelForm.promotionActivated ? levelName(levelForm.oldLevel) : '未进入奖金体系'" disabled />
        </el-form-item>
        <el-form-item label="调整为" required>
          <el-select v-model="levelForm.level" placeholder="请选择1–8级" style="width:100%">
            <el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="levelForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="将写入会员变更日志和后台操作日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelVisible=false">取消</el-button>
        <el-button type="primary" :loading="levelLoading" @click="submitLevelAdjust">直接生效</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assetVisible" :title="assetDialogTitle" width="520px" destroy-on-close>
      <el-alert title="提交前会再次确认；确认后余额立即生效，并写入余额流水和后台操作日志。" type="warning" :closable="false" show-icon />
      <el-form :model="assetForm" label-width="105px" class="level-form">
        <el-form-item label="会员"><el-input :model-value="`${assetForm.memberName}（${assetForm.memberAccount}）`" disabled /></el-form-item>
        <el-form-item label="当前数额"><el-input :model-value="money(assetForm.currentAmount)" disabled /></el-form-item>
        <el-form-item label="调整数量" required><el-input-number v-model="assetForm.amount" :min="0.01" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="调整原因" required><el-input v-model="assetForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="会写入余额流水和后台操作日志" /></el-form-item>
        <el-form-item label="管理员密码" required>
          <el-input v-model="assetForm.adminPassword" type="password" show-password maxlength="64" autocomplete="current-password" placeholder="二次验证当前管理员登录密码" />
          <div class="form-tip">密码只用于本次服务器身份校验，不写入余额流水和操作日志。</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="assetVisible=false">取消</el-button><el-button type="primary" :loading="assetLoading" @click="submitAssetAdjust">确认立即生效</el-button></template>
    </el-dialog>

    <el-dialog v-model="switchLineVisible" title="会员移线" width="680px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon class="line-change-notice">
        <template #title>移线规则与数据处理说明</template>
        <div class="line-change-rules">
          <p><strong>1. 上级关系：</strong>提交后，该会员及其完整下级团队会整体移到新直属上级名下；该会员的直属上级会变更，其下级成员之间原有的直属邀请关系保持不变，系统会按新位置重建整支团队的上级链。</p>
          <p><strong>2. 历史数据：</strong>移线前已经形成的订单、业绩、累计件数、已结算或待结算奖金及余额流水均保留原归属，不转给新上级，也不重新计算。</p>
          <p><strong>3. 已支付订单：</strong>以订单支付成功时冻结的关系为准。移线前已支付的订单，即使之后才确认收货、进入7天结算或发生退款，仍按移线前的上级链处理。</p>
          <p><strong>4. 新产生数据：</strong>移线生效后支付的订单（包括移线前创建但尚未支付的订单）按新关系链计算业绩、累计件数和奖金；新上级开始承接此后的团队数据，原上级不再承接新数据。</p>
          <p><strong>5. 操作结果：</strong>拥有移线管理权限时提交即生效，不再经过第二人审批；系统会保存操作人、原因及移线前后关系快照，操作不能自动撤销。</p>
        </div>
      </el-alert>
      <el-form :model="switchLineForm" label-width="105px" class="level-form">
        <el-form-item label="移线会员"><el-input :model-value="switchLineForm.agentName" disabled /></el-form-item>
        <el-form-item label="当前上级"><el-input :model-value="switchLineForm.parentName || '无上级'" disabled /></el-form-item>
        <el-form-item label="新直属上级" required>
          <el-select v-model="switchLineForm.newParentAgentId" filterable remote :remote-method="searchAgentOptions" placeholder="输入登录账号、手机号或名称搜索" style="width:100%">
            <el-option v-for="item in agentOptions" :key="item.id" :value="item.id" :label="`${item.agentName}（${item.memberAccount || '-'}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="移线原因" required><el-input v-model="switchLineForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="switchLineVisible=false">取消</el-button>
        <el-button type="primary" :loading="switchLineLoading" @click="submitSwitchLine">确认并立即移线</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="phoneVisible" title="修改会员登录手机号" width="540px" destroy-on-close>
      <el-alert title="修改后新手机号立即成为登录手机号；会员当前所有登录会话会失效，需要使用新手机号重新登录。" type="warning" :closable="false" show-icon />
      <el-form :model="phoneForm" label-width="115px" class="level-form">
        <el-form-item label="会员"><el-input :model-value="`${phoneForm.memberName}（${phoneForm.memberAccount}）`" disabled /></el-form-item>
        <el-form-item label="当前手机号"><el-input v-model="phoneForm.oldPhone" disabled /></el-form-item>
        <el-form-item label="新手机号" required><el-input v-model="phoneForm.phone" maxlength="11" inputmode="numeric" placeholder="请输入客户新的11位手机号" @input="value => phoneForm.phone = normalizeMainlandPhone(value)" /></el-form-item>
        <el-form-item label="修改原因" required><el-input v-model="phoneForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="例如：客户原手机号停用，经本人核实申请变更" /></el-form-item>
        <el-form-item label="管理员密码" required>
          <el-input v-model="phoneForm.adminPassword" type="password" show-password maxlength="64" autocomplete="current-password" placeholder="二次验证当前管理员登录密码" />
          <div class="form-tip">只验证本次操作，不会写入会员资料或操作日志。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="phoneLoading" @click="phoneVisible=false">取消</el-button>
        <el-button type="primary" :loading="phoneLoading" @click="submitPhoneUpdate">确认修改手机号</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置会员登录密码" width="540px" destroy-on-close>
      <el-alert title="这里重置的是商城登录密码，不是6位支付密码。原密码无法查看；重置后会员当前所有登录会话立即失效。" type="warning" :closable="false" show-icon />
      <el-form :model="passwordForm" label-width="115px" class="level-form">
        <el-form-item label="会员"><el-input :model-value="`${passwordForm.memberName}（${passwordForm.memberAccount}）`" disabled /></el-form-item>
        <el-form-item label="新登录密码" required><el-input v-model="passwordForm.newPassword" type="password" show-password maxlength="32" autocomplete="new-password" placeholder="6至32位" /></el-form-item>
        <el-form-item label="再次确认" required><el-input v-model="passwordForm.confirmPassword" type="password" show-password maxlength="32" autocomplete="new-password" placeholder="请再次输入新密码" /></el-form-item>
        <el-form-item label="重置原因" required><el-input v-model="passwordForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="例如：客户完成身份核实后申请后台重置" /></el-form-item>
        <el-form-item label="管理员密码" required>
          <el-input v-model="passwordForm.adminPassword" type="password" show-password maxlength="64" autocomplete="current-password" placeholder="二次验证当前管理员登录密码" />
          <div class="form-tip">重置完成后请通过安全方式告知客户，并提醒客户登录后立即自行修改。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="passwordLoading" @click="passwordVisible=false">取消</el-button>
        <el-button type="danger" :loading="passwordLoading" @click="submitPasswordReset">确认重置登录密码</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="后台新增商城会员" width="620px" destroy-on-close>
      <el-alert title="登录账号和手机号必填。填写初始密码后可使用登录账号或手机号登录；不填写则先通过手机号验证码登录。不开通会员身份时，完成首笔有效支付后才成为正式会员。" type="info" :closable="false" show-icon />
      <el-form :model="createForm" label-width="120px" class="create-form">
        <el-form-item label="登录账号" required>
          <el-input v-model="createForm.username" maxlength="64" placeholder="2至64个字符，不能与手机号相同" />
          <div class="form-tip">用于会员登录和后台识别，创建前会校验是否重复。</div>
        </el-form-item>
        <el-form-item label="手机号" required><el-input v-model="createForm.phone" maxlength="11" inputmode="numeric" placeholder="请输入11位手机号" @input="value => createForm.phone = normalizeMainlandPhone(value)" /></el-form-item>
        <el-form-item label="初始登录密码">
          <el-input v-model="createForm.password" type="password" show-password maxlength="32" autocomplete="new-password" placeholder="选填，6至32位" />
          <div class="form-tip">填写后可使用登录账号或手机号加密码登录；留空则先使用手机号验证码登录。</div>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" maxlength="64" placeholder="选填，可填写中文或英文；留空则使用登录账号" />
          <div class="form-tip">昵称是展示名称，不用于登录，也不要求与登录账号相同。</div>
        </el-form-item>
        <el-form-item label="邀请会员">
          <el-select v-model="createForm.inviterUserId" filterable remote clearable :remote-method="searchInviters" placeholder="按登录账号、手机号或昵称搜索" style="width:100%">
            <el-option v-for="item in inviterOptions" :key="item.id" :value="item.userId" :label="`${item.nickname || item.phone}（${item.username || item.phone}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="直接设为会员"><el-switch v-model="createForm.activateDistribution" /><div class="form-tip">后台例外操作；普通用户必须完成首笔有效支付后才成为会员。</div></el-form-item>
        <template v-if="createForm.activateDistribution">
          <el-form-item label="初始卡级" required><el-select v-model="createForm.initialLevel" style="width:100%"><el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="操作原因" required><el-input v-model="createForm.reason" type="textarea" :rows="3" placeholder="将写入调级操作日志" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="createLoading" @click="submitCreate">直接创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createShopMember, getShopMemberProfile, listShopMembers, resetShopMemberLoginPassword, unlockShopMember, updateShopMemberLevel, updateShopMemberPhone, updateShopMemberStatus } from '@/api/shop'
import { getPersonProfile } from '@/api/audit'
import { useAppStore } from '@/store'
import { listAgents, switchLine } from '@/api/agent'
import { deductAsset, issueAsset } from '@/api/assets'
import { memberSearchEmptyText, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'

const loading = ref(false)
const route = useRoute()
const router = useRouter()
const store = useAppStore()
const canManageDistribution = store.hasPermission('distribution:manage')
const canApplyLineChange = store.hasPermission('line-change:apply')
const canManageAssets = store.hasPermission('finance:manage')
const canReadFinance = store.hasPermission('finance:read')
const profileLoading = ref(false)
const profileVisible = ref(false)
const profileMode = ref('view')
const profileTab = ref('overview')
const createVisible = ref(false)
const createLoading = ref(false)
const levelVisible = ref(false)
const levelLoading = ref(false)
const switchLineVisible = ref(false)
const switchLineLoading = ref(false)
const assetVisible = ref(false)
const assetLoading = ref(false)
const phoneVisible = ref(false)
const phoneLoading = ref(false)
const passwordVisible = ref(false)
const passwordLoading = ref(false)
const members = ref([])
const searchFeedback = ref('')
const memberEmptyText = ref('暂无商城会员')
const profile = ref({})
const financeProfile = ref({})
const currentMember = ref({})
const query = ref({ keyword: '', status: null, promotionActivated: null, agentLevel: null })
const pagination = ref({ page: 1, size: 10, total: 0 })
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => {
    pagination.value.page = 1
    fetchMembers()
  },
)
const inviterOptions = ref([])
const agentOptions = ref([])
const levels = [
  { value: 1, label: '会员' }, { value: 2, label: 'VIP会员' }, { value: 3, label: '店铺' }, { value: 4, label: '代理' },
  { value: 5, label: '一星董事' }, { value: 6, label: '二星董事' }, { value: 7, label: '三星董事' }, { value: 8, label: '合伙人' },
]
const emptyCreateForm = () => ({ username: '', phone: '', password: '', nickname: '', inviterUserId: null, activateDistribution: false, initialLevel: 1, reason: '' })
const createForm = ref(emptyCreateForm())
const levelForm = ref({ memberId: null, memberAccount: '', memberName: '', promotionActivated: false, oldLevel: null, level: null, reason: '' })
const switchLineForm = ref({ agentId: null, agentName: '', parentName: '', newParentAgentId: null, reason: '' })
const assetForm = ref({ operationType: 'ISSUE', agentId: null, memberId: null, memberAccount: '', memberName: '', currentAmount: 0, amount: 0, reason: '', adminPassword: '', requestId: '' })
const phoneForm = ref({ memberId: null, memberAccount: '', memberName: '', oldPhone: '', phone: '', reason: '', adminPassword: '' })
const passwordForm = ref({ memberId: null, memberAccount: '', memberName: '', newPassword: '', confirmPassword: '', reason: '', adminPassword: '' })
const assetDialogTitle = computed(() => `${assetForm.value.operationType === 'ISSUE' ? '增加' : '扣减'}余额`)
const financeAssetFlows = computed(() => (financeProfile.value.assetFlows || []).filter((item) => item.assetCode === 'CASH_BONUS'))
const financeBalance = computed(() => (financeProfile.value.assetAccounts || []).find((item) => item.assetCode === 'CASH_BONUS')?.balance || 0)
const isEditMode = computed(() => profileMode.value === 'edit')
const profileDialogTitle = computed(() => {
  const account = currentMember.value.username || currentMember.value.nickname || currentMember.value.phone || '会员'
  return isEditMode.value ? `编辑会员 · ${account}` : `会员全景 · ${account}`
})

const money = (value) => Number(value || 0).toFixed(2)
const normalizedText = (value) => String(value || '').trim()
const memberDisplayName = (row) => {
  const nickname = normalizedText(row?.nickname)
  const phone = normalizedText(row?.phone)
  return nickname && nickname !== phone ? nickname : '未设置会员名称'
}
const memberNameHint = (row) => memberDisplayName(row) === '未设置会员名称' ? '可在编辑中补充昵称' : ''
const inviterDisplayName = (row) => {
  const name = normalizedText(row?.inviterName)
  const account = normalizedText(row?.inviterMemberAccount)
  const phone = normalizedText(row?.inviterPhone)
  if (name && name !== account && name !== phone) return name
  return account || phone || '未知会员'
}
const inviterIdentityText = (row) => {
  const displayName = inviterDisplayName(row)
  const account = normalizedText(row?.inviterMemberAccount)
  const phone = normalizedText(row?.inviterPhone)
  const identities = []
  if (account && account !== displayName && account !== phone) identities.push(`账号：${account}`)
  if (phone && phone !== displayName) identities.push(`手机：${phone}`)
  return identities.join(' · ') || '直属邀请会员'
}
const balanceOf = (accounts = []) => (accounts || []).find((item) => item.assetCode === 'CASH_BONUS')?.balance || 0
const levelName = (value) => levels.find((item) => item.value === Number(value))?.label || '-'
const accountStatusName = (row) => Number(row?.status) === 0 ? '账号禁用' : (row?.loginLocked ? '登录锁定' : '账号正常')
const accountStatusTag = (row) => Number(row?.status) === 0 ? 'info' : (row?.loginLocked ? 'danger' : 'success')
const orderStatus = (status) => ({ 0: '已取消', 1: '待发货', 2: '已发货', 3: '已完成', 4: '售后关闭' }[status] || '处理中')
const orderTag = (status) => ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[status] || 'info')
const joinAddress = (address) => [address.province, address.city, address.district, address.detailAddress].filter(Boolean).join('')
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const bonusTypeText = (row) => row.bonusType === 'DIRECT_REWARD'
  ? '直推奖'
  : row.bonusType === 'DIRECTOR_SHARE' ? '董事团队分红' : '历史奖金'
const isIncome = (type) => [1, 4].includes(Number(type))
const assetFlowSourceText = (row) => {
  if (String(row.bizType || '').endsWith('MANUAL_MEMBER_ADJUST')) {
    return isIncome(row.changeType) ? '后台人工增加' : '后台人工扣减'
  }
  return ({
    COMMISSION_SETTLE: '奖金结算入账',
    MEMBER_BALANCE_TRANSFER: Number(row.changeType) === 4 ? '会员转入' : '转给会员',
    ORDER_BALANCE_PAYMENT: '商城余额支付',
    BALANCE_PAYMENT_REFUND: '订单退款退回',
    WITHDRAW_APPLY: '申请提现',
    WITHDRAW_REJECT_REFUND: '提现驳回退回',
    COMMISSION_CLAWBACK: '退款追回已结算奖金',
    ORDER_BALANCE_ALLOCATION: '订单成本/剩余款入账',
    ORDER_BALANCE_ALLOCATION_REFUND: '订单成本/剩余款退款冲回',
  }[row.bizType] || '其他余额变动')
}

const fetchMembers = async () => {
  const validation = validateMemberSearch(query.value.keyword)
  if (!validation.valid) {
    members.value = []
    pagination.value.total = 0
    searchFeedback.value = validation.message
    memberEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.value.keyword = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  memberEmptyText.value = validation.keyword
    ? memberSearchEmptyText(validation.keyword, '商城会员')
    : '暂无商城会员'
  loading.value = true
  try {
    const res = await listShopMembers({
      ...query.value,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    members.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchMembers()
}

const resetQuery = () => {
  query.value = { keyword: '', status: null, promotionActivated: null, agentLevel: null }
  pagination.value.page = 1
  fetchMembers()
}

const loadProfileData = async (row) => {
  profileLoading.value = true
  try {
    const res = await getShopMemberProfile(row.id)
    profile.value = res.data || {}
    financeProfile.value = {}
    if (row.promotionActivated && canReadFinance) {
      const financeRes = await getPersonProfile({ keyword: row.username || row.phone })
      financeProfile.value = financeRes.data || {}
    }
  } finally {
    profileLoading.value = false
  }
}

const openProfile = async (row, mode = 'view') => {
  currentMember.value = { ...row }
  profileMode.value = mode === 'edit' ? 'edit' : 'view'
  profileTab.value = 'overview'
  profileVisible.value = true
  await loadProfileData(currentMember.value)
}

const refreshCurrentProfile = async () => {
  await fetchMembers()
  const refreshed = members.value.find((item) => item.id === currentMember.value.id)
  if (refreshed) currentMember.value = { ...refreshed }
  if (profileVisible.value && currentMember.value.id) await loadProfileData(currentMember.value)
}

const toggleStatus = async (row) => {
  await updateShopMemberStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('会员状态已更新')
  fetchMembers()
}

const unlockMember = async (row) => {
  await unlockShopMember(row.id)
  ElMessage.success('登录锁定已解除，密码错误次数已清零')
  await refreshCurrentProfile()
}

const openAccountSecurity = (row, command) => {
  const memberName = row.nickname || row.username || row.phone
  if (command === 'PHONE') {
    phoneForm.value = {
      memberId: row.id,
      memberAccount: row.memberAccount,
      memberName,
      oldPhone: row.phone,
      phone: '',
      reason: '',
      adminPassword: '',
    }
    phoneVisible.value = true
    return
  }
  passwordForm.value = {
    memberId: row.id,
    memberAccount: row.memberAccount,
    memberName,
    newPassword: '',
    confirmPassword: '',
    reason: '',
    adminPassword: '',
  }
  passwordVisible.value = true
}

const submitPhoneUpdate = async () => {
  const data = phoneForm.value
  if (!isValidMainlandPhone(data.phone)) return ElMessage.warning('请输入正确的11位新手机号')
  if (data.phone === data.oldPhone) return ElMessage.warning('新手机号不能与当前手机号相同')
  if (!data.reason.trim()) return ElMessage.warning('请填写修改手机号的原因')
  if (!data.adminPassword) return ElMessage.warning('请输入当前管理员登录密码进行二次验证')
  try {
    await ElMessageBox.confirm(
      `确认将“${data.memberName}（${data.memberAccount}）”的登录手机号修改为 ${data.phone} 吗？修改后该会员需要重新登录。`,
      '再次确认修改手机号',
      { confirmButtonText: '确认修改', cancelButtonText: '返回检查', type: 'warning' },
    )
  } catch {
    return
  }
  phoneLoading.value = true
  try {
    await updateShopMemberPhone(data.memberId, {
      phone: data.phone,
      reason: data.reason.trim(),
      adminPassword: data.adminPassword,
    })
    phoneForm.value.adminPassword = ''
    phoneVisible.value = false
    ElMessage.success('会员登录手机号已修改，旧登录会话已失效')
    await refreshCurrentProfile()
  } finally {
    phoneLoading.value = false
  }
}

const submitPasswordReset = async () => {
  const data = passwordForm.value
  if (!data.newPassword || data.newPassword.length < 6 || data.newPassword.length > 32) return ElMessage.warning('新登录密码需要6至32位')
  if (data.newPassword !== data.confirmPassword) return ElMessage.warning('两次输入的新登录密码不一致')
  if (!data.reason.trim()) return ElMessage.warning('请填写重置登录密码的原因')
  if (!data.adminPassword) return ElMessage.warning('请输入当前管理员登录密码进行二次验证')
  try {
    await ElMessageBox.confirm(
      `确认重置“${data.memberName}（${data.memberAccount}）”的商城登录密码吗？确认后旧密码及全部旧登录会话立即失效。`,
      '再次确认重置登录密码',
      { confirmButtonText: '确认重置', cancelButtonText: '返回检查', type: 'warning' },
    )
  } catch {
    return
  }
  passwordLoading.value = true
  try {
    await resetShopMemberLoginPassword(data.memberId, {
      newPassword: data.newPassword,
      reason: data.reason.trim(),
      adminPassword: data.adminPassword,
    })
    passwordForm.value = { memberId: null, memberAccount: '', memberName: '', newPassword: '', confirmPassword: '', reason: '', adminPassword: '' }
    passwordVisible.value = false
    ElMessage.success('会员登录密码已重置，旧登录会话已失效')
    await refreshCurrentProfile()
  } finally {
    passwordLoading.value = false
  }
}

const openLevelAdjust = (row) => {
  levelForm.value = {
    memberId: row.id,
    memberAccount: row.memberAccount,
    memberName: row.nickname || row.username || row.phone,
    promotionActivated: Boolean(row.promotionActivated),
    oldLevel: row.agentLevel,
    level: row.promotionActivated ? row.agentLevel : null,
    reason: '',
  }
  levelVisible.value = true
}

const openAssetAdjust = (row, command) => {
  assetForm.value = {
    operationType: command,
    agentId: row.agentId,
    memberId: row.id,
    memberAccount: row.memberAccount,
    memberName: row.nickname || row.username || row.phone,
    currentAmount: row.availableBalance,
    amount: 0,
    reason: '',
    adminPassword: '',
    requestId: crypto.randomUUID(),
  }
  assetVisible.value = true
}

const submitAssetAdjust = async () => {
  if (!assetForm.value.amount || Number(assetForm.value.amount) <= 0) return ElMessage.warning('请输入大于0的调整数量')
  if (!assetForm.value.reason.trim()) return ElMessage.warning('请填写调整原因')
  if (!assetForm.value.adminPassword) return ElMessage.warning('请输入当前管理员登录密码进行二次验证')
  const action = assetForm.value.operationType === 'ISSUE' ? '增加' : '扣减'
  try {
    await ElMessageBox.confirm(
      `确认给“${assetForm.value.memberName}（${assetForm.value.memberAccount}）”${action}余额 ¥${money(assetForm.value.amount)} 吗？确认后立即生效。`,
      '再次确认余额调整',
      { confirmButtonText: '确认立即生效', cancelButtonText: '返回修改', type: 'warning' },
    )
  } catch {
    return
  }
  assetLoading.value = true
  try {
    const request = assetForm.value.operationType === 'ISSUE' ? issueAsset : deductAsset
    await request({
      agentId: assetForm.value.agentId,
      amount: assetForm.value.amount,
      bizType: 'MANUAL_MEMBER_ADJUST',
      bizId: String(assetForm.value.memberId),
      requestId: assetForm.value.requestId,
      remark: assetForm.value.reason.trim(),
      adminPassword: assetForm.value.adminPassword,
    })
    ElMessage.success(`余额已${assetForm.value.operationType === 'ISSUE' ? '增加' : '扣减'}并立即生效`)
    assetVisible.value = false
    await refreshCurrentProfile()
  } finally {
    assetLoading.value = false
  }
}

const submitLevelAdjust = async () => {
  if (!levelForm.value.level) return ElMessage.warning('请选择目标卡级')
  if (levelForm.value.promotionActivated && Number(levelForm.value.level) === Number(levelForm.value.oldLevel)) {
    return ElMessage.warning('请选择不同的目标卡级')
  }
  if (!levelForm.value.reason.trim()) return ElMessage.warning('请填写调整原因')
  levelLoading.value = true
  try {
    await updateShopMemberLevel(levelForm.value.memberId, {
      level: levelForm.value.level,
      reason: levelForm.value.reason.trim(),
    })
    ElMessage.success(levelForm.value.promotionActivated ? '卡级已调整并记录日志' : '已开通推广身份并设定卡级')
    levelVisible.value = false
    await refreshCurrentProfile()
  } finally {
    levelLoading.value = false
  }
}

const openTeam = (row) => {
  profileVisible.value = false
  router.push({ path: '/members/tree', query: { memberAccount: row.memberAccount || row.username || row.phone || '' } })
}

const openSwitchLine = (row) => {
  if (row.hasPendingLineChange) {
    ElMessage.warning('该会员有待移线处理申请，暂不可再进行移线操作')
    return
  }
  switchLineForm.value = {
    agentId: row.agentId,
    agentName: `${row.nickname || row.username || row.phone}（${row.username || row.phone}）`,
    parentName: row.parentName,
    newParentAgentId: null,
    reason: '',
  }
  agentOptions.value = []
  switchLineVisible.value = true
}

const searchAgentOptions = async (keyword) => {
  if (!keyword) return (agentOptions.value = [])
  const res = await listAgents({ keyword, status: 1, pageNum: 1, pageSize: 30 })
  agentOptions.value = (res.data?.list || []).filter((item) => item.id !== switchLineForm.value.agentId)
}

const submitSwitchLine = async () => {
  if (!switchLineForm.value.newParentAgentId) return ElMessage.warning('请选择新的直属上级')
  if (!switchLineForm.value.reason.trim()) return ElMessage.warning('请填写移线原因')
  await ElMessageBox.confirm(
    '提交后本人及完整下级团队会立即移到新直属上级名下。历史数据保留原归属；移线前已支付订单按旧关系处理，移线后支付订单按新关系处理。此操作不能自动撤销。',
    '确认立即移线',
    { type: 'warning', confirmButtonText: '确认并立即移线', cancelButtonText: '取消' },
  )
  switchLineLoading.value = true
  try {
    await switchLine({
      agentId: switchLineForm.value.agentId,
      newParentAgentId: switchLineForm.value.newParentAgentId,
      reason: switchLineForm.value.reason.trim(),
    })
    ElMessage.success('移线已执行并记录操作日志')
    switchLineVisible.value = false
    await refreshCurrentProfile()
  } finally {
    switchLineLoading.value = false
  }
}

const openCreate = () => { createForm.value = emptyCreateForm(); inviterOptions.value = []; createVisible.value = true }
const searchInviters = async (keyword) => {
  if (!keyword) return (inviterOptions.value = [])
  const res = await listShopMembers({ keyword, status: 1, pageNum: 1, pageSize: 30 })
  inviterOptions.value = res.data?.list || []
}
const submitCreate = async () => {
  const username = createForm.value.username.trim()
  if (username.length < 2 || username.length > 64) return ElMessage.warning('请输入2至64个字符的登录账号')
  if (!isValidMainlandPhone(createForm.value.phone)) return ElMessage.warning('请输入正确的11位手机号')
  if (username === createForm.value.phone) return ElMessage.warning('登录账号不能与手机号相同')
  createForm.value.username = username
  createForm.value.nickname = createForm.value.nickname.trim()
  if (createForm.value.password && (createForm.value.password.length < 6 || createForm.value.password.length > 32)) return ElMessage.warning('初始密码需要6至32位')
  if (createForm.value.activateDistribution && !createForm.value.reason.trim()) return ElMessage.warning('请填写开通和调级原因')
  createLoading.value = true
  try {
    await createShopMember(createForm.value)
    ElMessage.success('会员已创建，可使用登录账号或手机号登录')
    createVisible.value = false
    fetchMembers()
  } finally { createLoading.value = false }
}

onMounted(async () => {
  await fetchMembers()
  if (route.query.create === '1') {
    openCreate()
    router.replace('/members/list')
  }
})
</script>

<style scoped>
.member-name {
  font-weight: 600;
  color: #303133;
}
.account-tip { margin-bottom: 16px; }
.search-feedback { margin-bottom: 16px; }

.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.block {
  margin-top: 16px;
}
.profile-actions-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  background: #f4f9ff;
}
.profile-actions-heading { display:flex; flex-direction:column; gap:4px; min-width:210px; }
.profile-actions-heading strong { color:#303133; font-size:15px; }
.profile-actions-heading span { color:#909399; font-size:12px; }
.profile-actions-buttons { display:flex; justify-content:flex-end; align-items:center; flex-wrap:wrap; gap:10px; }
.profile-actions-buttons :deep(.el-button),
.profile-actions-buttons :deep(.el-dropdown) { margin:0; }
.profile-tabs { margin-top: 4px; }
.profile-finance-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.profile-finance-metrics > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}
.profile-finance-metrics span { color:#909399; font-size:13px; }
.profile-finance-metrics strong { color:#303133; font-size:20px; }
.success-text { color:#67c23a !important; }
.warning-text { color:#e6a23c !important; }
.danger-text { color:#f56c6c !important; }
.create-form { margin-top: 20px; }
.level-form { margin-top: 20px; }
.line-change-notice { margin-bottom: 20px; }
.line-change-rules { padding-top: 4px; color: #7a4f01; font-size: 13px; line-height: 1.65; }
.line-change-rules p { margin: 4px 0; }
.form-tip { width:100%; color:#909399; font-size:12px; line-height:18px; margin-top:5px; }
.migration-card { border-color: #b3d8ff; }
.baseline-note { margin-top: 12px; }
.operation-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 12px;
  white-space: nowrap;
}

.operation-actions :deep(.el-button),
.operation-actions :deep(.el-dropdown) {
  margin: 0;
  font-family: inherit;
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
}

.operation-actions :deep(.el-button) {
  height: 20px;
  padding: 0;
}
.action-tooltip-trigger { display: inline-flex; }
@media (max-width: 960px) {
  .profile-actions-panel { align-items:flex-start; flex-direction:column; }
  .profile-actions-buttons { justify-content:flex-start; }
  .profile-finance-metrics { grid-template-columns:repeat(2, minmax(0, 1fr)); }
}
</style>
