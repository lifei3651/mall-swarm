<template>
  <div class="page-container">
    <el-alert
      v-if="isMerchantUser"
      :title="merchantScopeTip"
      type="info"
      :closable="false"
      show-icon
      class="merchant-order-scope-tip"
    />
    <nav class="order-state-nav" aria-label="订单状态筛选">
      <button
        v-for="item in orderStateOptions"
        :key="item.value"
        type="button"
        :class="{ active: query.orderState === item.value }"
        @click="changeOrderState(item.value)"
      >
        <span>{{ item.label }}</span>
        <span v-if="orderStateCount(item.value) > 0" class="order-state-count">
          {{ orderStateCount(item.value) }}
        </span>
      </button>
    </nav>

    <div class="search-container order-search-panel">
      <el-form :inline="true" :model="query">
        <el-form-item label="订单搜索">
          <el-input v-model="query.keyword" placeholder="请输入订单号、联合支付单号、收货人、手机号或客服备注" clearable @keyup.enter="handleOrderSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="orderLoading" @click="handleOrderSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetOrderQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="order-batch-actions">
          <el-button :icon="Download" :loading="exportLoading" @click="handleExportOrders">导出订单</el-button>
          <template v-if="query.orderState === 'PENDING_SHIPMENT' && merchantFulfillmentAllowed">
          <el-tooltip content="表格只处理订单号、物流公司、物流单号和发货数量" placement="top">
            <el-button type="success" plain :icon="Download" :loading="templateLoading" @click="handleDownloadShipmentTemplate">下载发货表</el-button>
          </el-tooltip>
          <el-tooltip content="下载空白模板和填写说明，不包含真实订单数据" placement="top">
            <el-button plain :icon="Download" :loading="importTemplateLoading" @click="handleDownloadShipmentImportTemplate">下载导入模板</el-button>
          </el-tooltip>
          <el-upload
            accept=".xlsx,.xls"
            :show-file-list="false"
            :http-request="handleShipmentImport"
            :disabled="importLoading"
          >
            <el-button type="warning" plain :icon="Upload" :loading="importLoading">导入物流并发货</el-button>
          </el-upload>
          </template>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="query.orderState === 'PENDING_SHIPMENT'"
        title="系统只读取订单号、物流公司、物流单号和发货数量。错误行会单独跳过，不影响其他正确行发货；拆成多个包裹时复制订单行，多个订单合箱时可填写相同物流信息。"
        type="info"
        :closable="false"
        show-icon
        class="shipping-workflow-tip"
      />
    </div>

    <el-alert v-if="orderSearchFeedback" :title="orderSearchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table class="order-table" :data="orders" v-loading="orderLoading" :empty-text="orderEmptyText" style="width: 100%">
          <el-table-column label="商品名称" min-width="190">
            <template #default="{ row }">
              <div class="order-item-cell-list">
                <div v-for="item in row.items || []" :key="item.id" class="order-item-cell product-name-item">
                  <strong>{{ item.productName || '商品' }}</strong>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="商品规格" min-width="125">
            <template #default="{ row }">
              <div class="order-item-cell-list">
                <div v-for="item in row.items || []" :key="item.id" class="order-item-cell product-spec-item">
                  {{ formatProductSpec(item) }}
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="商品数量" width="82" align="center">
            <template #default="{ row }">
              <div class="order-item-cell-list">
                <div v-for="item in row.items || []" :key="item.id" class="order-item-cell product-quantity-item">
                  {{ Number(item.quantity || 0) }} 件
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="订单编号" min-width="175">
            <template #default="{ row }">
              <div class="order-no">{{ row.order?.orderNo }}</div>
              <div v-if="row.order?.tradeNo" class="sub trade-no">联合支付 {{ row.order.tradeNo }}</div>
              <el-tag v-if="row.order?.tradeId" size="small" effect="plain" type="info">商户子订单</el-tag>
              <el-button v-if="row.order?.tradeId && !isMerchantUser" type="primary" link size="small" @click.stop="openTradeDetail(row.order.tradeId)">查看联合单</el-button>
              <el-tag v-if="row.order?.businessType && row.order.businessType !== 'NORMAL'" size="small" effect="plain" :type="row.order.businessType === 'FLASH_SALE' ? 'danger' : 'warning'">{{ row.order.businessType === 'FLASH_SALE' ? '秒杀订单' : '复购订单' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="购买账号" min-width="130">
            <template #default="{ row }">
              <span class="buyer-account">{{ row.memberAccount || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="履约状态" width="100">
            <template #default="{ row }">
              <el-tag :type="orderDisplayTag(row)">{{ orderDisplayStatus(row) }}</el-tag>
              <el-tag v-if="isMerchantUser && row.merchantFulfillmentAllowed === false" size="small" type="danger" effect="plain">平台接管</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="售后 / 退款" width="155">
            <template #default="{ row }">
              <div v-if="activeAfterSale(row)" class="after-sale-summary">
                <el-tag size="small" :type="afterSaleTag(activeAfterSale(row).status)">
                  {{ afterSaleStatus(activeAfterSale(row).status, activeAfterSale(row).applyType) }}
                </el-tag>
                <div v-if="Number(activeAfterSale(row).applyType) === 3" class="sub">同规格换货 {{ Number(activeAfterSale(row).refundQuantity || 0) }} 件 · 不退款</div>
                <div v-else class="sub">申请 {{ Number(activeAfterSale(row).refundQuantity || 0) }} 件 · ¥{{ money(activeAfterSale(row).refundAmount) }}</div>
                <div v-if="activeAfterSale(row).nextActionHint" class="after-sale-action-deadline" :class="{ overdue: activeAfterSale(row).nextActionOverdue }">
                  {{ activeAfterSale(row).nextActionHint }}
                  <span v-if="activeAfterSale(row).nextActionDeadline">{{ formatDateTime(activeAfterSale(row).nextActionDeadline) }} 截止</span>
                </div>
              </div>
              <div v-if="hasApprovedRefund(row)" class="refund-summary">
                <el-tag size="small" :type="isFullRefund(row) ? 'danger' : 'warning'">
                  {{ refundResultLabel(row) }}
                </el-tag>
                <div class="sub">实退 ¥{{ money(approvedRefundAmount(row)) }} · {{ approvedRefundQuantity(row) }} 件</div>
              </div>
              <span v-if="!activeAfterSale(row) && !hasApprovedRefund(row)">-</span>
            </template>
          </el-table-column>
          <el-table-column label="订单总金额" width="110">
            <template #default="{ row }">
              <span>¥{{ money(row.order?.payAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!isMerchantUser" label="奖金总拨出" width="110">
            <template #default="{ row }">
              <span :class="{ danger: payoutExceeded(row.order?.payAmount, row.finance?.bonusAmount) }">
                ¥{{ money(row.finance?.bonusAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="物流信息" width="155">
            <template #default="{ row }">
              <div v-if="shipmentRows(row).length" class="shipment-list">
                <div v-for="(shipment, index) in shipmentRows(row)" :key="`${shipment.deliveryCompany}-${shipment.deliveryNo}-${index}`">
                  <span>{{ shipment.deliveryCompany || '-' }}</span>
                  <div class="sub">{{ shipment.deliveryNo || '-' }}</div>
                  <div class="sub">发货 {{ shipment.shipmentQuantity || 0 }} 件</div>
                </div>
                <div v-if="row.autoReceiveEnabled" class="sub auto-receive-deadline">
                  {{ formatDateTime(row.autoReceiveDeadline) }} 自动收货
                </div>
                <el-button type="primary" link size="small" @click="openTracking(row)">查看轨迹</el-button>
              </div>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.order?.createTime) }}</template>
          </el-table-column>
          <el-table-column label="收货信息" min-width="190">
            <template #default="{ row }">
              <div>{{ row.order?.receiverName }} {{ row.order?.receiverPhone }}</div>
              <div class="sub">{{ row.order?.receiverAddress }}</div>
            </template>
          </el-table-column>
          <el-table-column label="客服备注" fixed="right" width="180">
            <template #default="{ row }">
              <div class="service-remark-cell">
                <el-tooltip v-if="row.serviceRemark" :content="row.serviceRemark" placement="top" :show-after="300">
                  <span class="service-remark-preview">{{ row.serviceRemark }}</span>
                </el-tooltip>
                <span v-else class="sub">暂无备注</span>
                <el-button type="primary" link @click.stop="openServiceRemark(row)">
                  {{ row.serviceRemark ? '修改' : '添加' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="165">
            <template #default="{ row }">
              <div class="order-actions">
                <el-dropdown
                  v-if="canMerchantFulfill(row) && Number(activeAfterSale(row)?.status) === 0"
                  trigger="click"
                  @command="handleAfterSaleCommand($event, activeAfterSale(row))"
                >
                  <el-button type="warning" link>处理售后</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item :command="1">审核通过</el-dropdown-item>
                      <el-dropdown-item :command="2">拒绝申请</el-dropdown-item>
                      <el-dropdown-item :command="3">关闭申请</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <template v-else-if="activeAfterSale(row)">
                  <el-tag v-if="Number(activeAfterSale(row).status) === 4" type="warning">等待客户寄回</el-tag>
                  <el-button v-else-if="canMerchantFulfill(row) && Number(activeAfterSale(row).status) === 5" type="success" link @click.stop="confirmReturnReceived(activeAfterSale(row))">
                    {{ Number(activeAfterSale(row).applyType) === 3 ? '确认收到换货退件' : '确认退货并退款' }}
                  </el-button>
                  <el-button v-else-if="canMerchantFulfill(row) && Number(activeAfterSale(row).status) === 6" type="warning" link @click.stop="confirmReturnReceived(activeAfterSale(row))">
                    重试渠道退款
                  </el-button>
                  <el-button v-else-if="canMerchantFulfill(row) && Number(activeAfterSale(row).status) === 7" type="primary" link @click.stop="openExchangeShipment(activeAfterSale(row))">
                    发出换货商品
                  </el-button>
                  <el-tag v-else-if="Number(activeAfterSale(row).status) === 8" type="primary">换货商品已发出</el-tag>
                  <el-tag v-else type="warning">处理中</el-tag>
                </template>
                <el-button v-if="canShipOrder(row)" type="primary" link @click="openShip(row)">
                  {{ shipmentRows(row).length ? '继续发货' : '发货' }}
                </el-button>
                <el-dropdown v-if="!isMerchantUser" trigger="click" @command="handleOrderMoreCommand($event, row)">
                  <el-button link>更多操作</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="BONUS">奖金去向</el-dropdown-item>
                      <el-dropdown-item v-if="canCancelAdminOrder(row)" command="CANCEL" divided>
                        {{ Number(row.order?.status) === 1 ? '取消并退款' : '取消订单' }}
                      </el-dropdown-item>
                      <el-dropdown-item v-if="canManualRefund(row)" command="REFUND" divided>后台退款</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
      @current-change="fetchOrders"
      @size-change="fetchOrders"
    />

    <el-dialog v-model="serviceRemarkDialogVisible" title="订单客服备注" width="520px" destroy-on-close>
      <el-alert title="此备注仅供后台客服和运营人员查看，不会展示给下单客户。" type="info" :closable="false" show-icon />
      <el-form label-width="88px" class="service-remark-form">
        <el-form-item label="订单号">
          <el-input :model-value="currentOrder?.order?.orderNo" disabled />
        </el-form-item>
        <el-form-item label="客户留言">
          <div class="customer-order-remark">{{ currentOrder?.order?.remark || '客户未填写留言' }}</div>
        </el-form-item>
        <el-form-item label="客服备注">
          <el-input
            v-model="serviceRemarkForm"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="例如：已电话确认改为周末配送；缺货时先联系客户，不要直接取消订单"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceRemarkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="serviceRemarkLoading" @click="submitServiceRemark">保存备注</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tradeDetailVisible" title="联合支付详情" width="920px" destroy-on-close>
      <div v-loading="tradeDetailLoading">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="联合支付单号">{{ tradeDetail.trade?.tradeNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">{{ tradeStatusLabel(tradeDetail.trade?.status) }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ payTypeLabel(tradeDetail.trade?.payType) }}</el-descriptions-item>
          <el-descriptions-item label="子订单">{{ tradeDetail.childCount || 0 }} 张</el-descriptions-item>
          <el-descriptions-item label="商品金额">¥{{ money(tradeDetail.trade?.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="运费">¥{{ money(tradeDetail.trade?.freightAmount) }}</el-descriptions-item>
          <el-descriptions-item label="一次实付">¥{{ money(tradeDetail.trade?.payAmount) }}</el-descriptions-item>
          <el-descriptions-item label="已完成退款">¥{{ money(tradeDetail.refundedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ formatDateTime(tradeDetail.trade?.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间" :span="2">{{ formatDateTime(tradeDetail.trade?.payTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="tradeDetail.childOrders || []" border class="trade-child-table" empty-text="暂无履约子订单">
          <el-table-column label="销售方" min-width="150"><template #default="{ row }">{{ row.order?.merchantName || '平台自营' }}</template></el-table-column>
          <el-table-column label="子订单号" min-width="190"><template #default="{ row }">{{ row.order?.orderNo }}</template></el-table-column>
          <el-table-column label="履约状态" width="105"><template #default="{ row }"><el-tag :type="orderDisplayTag(row)">{{ orderDisplayStatus(row) }}</el-tag></template></el-table-column>
          <el-table-column label="实付金额" width="110"><template #default="{ row }">¥{{ money(row.order?.payAmount) }}</template></el-table-column>
          <el-table-column label="退款金额" width="110"><template #default="{ row }">¥{{ money(approvedRefundAmount(row)) }}</template></el-table-column>
          <el-table-column label="物流包裹" width="100"><template #default="{ row }">{{ shipmentRows(row).length }} 个</template></el-table-column>
        </el-table>
      </div>
      <template #footer><el-button type="primary" @click="tradeDetailVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="trackingVisible" title="物流轨迹" width="680px" destroy-on-close>
      <div v-loading="trackingLoading">
        <el-empty v-if="!trackingRows.length" description="暂无物流包裹" />
        <el-card v-for="item in trackingRows" :key="item.shipmentId" shadow="never" class="tracking-card">
          <template #header><strong>{{ item.deliveryCompany || '物流公司' }} · {{ item.deliveryNo || '-' }}</strong></template>
          <el-alert :title="item.statusText || '暂无轨迹'" :type="item.events?.length ? 'success' : 'info'" :closable="false" />
          <el-timeline v-if="item.events?.length" class="tracking-timeline">
            <el-timeline-item v-for="event in item.events" :key="`${event.eventTime}-${event.description}`" :timestamp="event.eventTime" placement="top">
              {{ event.description }}<span v-if="event.location"> · {{ event.location }}</span>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </div>
    </el-dialog>

    <el-dialog v-model="shipDialogVisible" :title="currentOrder?.order?.status === 2 ? '添加物流包裹' : '订单发货'" width="520px">
      <el-form :model="shipForm" label-width="92px">
        <el-form-item label="订单号">
          <el-input :model-value="currentOrder?.order?.orderNo" disabled />
        </el-form-item>
        <el-form-item v-if="shipmentRows(currentOrder).length" label="已有包裹">
          <div class="existing-shipments">
            <div v-for="(shipment, index) in shipmentRows(currentOrder)" :key="`${shipment.deliveryCompany}-${shipment.deliveryNo}-${index}`">
              包裹{{ index + 1 }}：{{ shipment.deliveryCompany }} / {{ shipment.deliveryNo }}
              / {{ shipment.shipmentQuantity || 0 }}件
            </div>
          </div>
        </el-form-item>
        <el-form-item label="物流公司" required>
          <el-select
            v-model="shipForm.deliveryCompany"
            filterable
            clearable
            placeholder="请选择物流公司"
            style="width: 100%"
          >
            <el-option
              v-for="company in logisticsCompanyOptions"
              :key="company"
              :label="company"
              :value="company"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="shipForm.deliveryNo" />
        </el-form-item>
        <el-form-item label="发货数量" required>
          <el-input-number v-model="shipForm.shipmentQuantity" :min="1" :max="Math.max(1, remainingShipmentQuantity(currentOrder))" :step="1" step-strictly />
          <span class="remaining-tip">剩余可发 {{ remainingShipmentQuantity(currentOrder) }} 件</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShip">{{ currentOrder?.order?.status === 2 ? '确认添加' : '确认发货' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipmentResultVisible" title="批量发货导入结果" width="720px">
      <el-alert
        :title="shipmentResult.message || '导入完成'"
        :type="shipmentResult.failedCount > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="4" border class="shipment-result-summary">
        <el-descriptions-item label="表格数据">{{ shipmentResult.totalRows || 0 }} 条</el-descriptions-item>
        <el-descriptions-item label="新增包裹记录">{{ shipmentResult.shippedCount || 0 }} 条</el-descriptions-item>
        <el-descriptions-item label="重复跳过">{{ shipmentResult.skippedCount || 0 }} 条</el-descriptions-item>
        <el-descriptions-item label="错误">{{ shipmentResult.failedCount || 0 }} 条</el-descriptions-item>
      </el-descriptions>
      <el-table
        v-if="shipmentResult.errors?.length"
        :data="shipmentResult.errors"
        max-height="360"
        class="shipment-error-table"
      >
        <el-table-column prop="rowNumber" label="Excel行号" width="100" />
        <el-table-column prop="orderNo" label="订单号" width="210">
          <template #default="{ row }">{{ row.orderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="错误原因" min-width="280" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="shipmentResultVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditDialogVisible" :title="auditDialogTitle" width="460px">
      <el-form :model="auditForm" label-width="92px">
        <el-form-item label="售后号">
          <el-input :model-value="currentAfterSale?.afterSaleNo" disabled />
        </el-form-item>
        <el-form-item label="申请原因">
          <div class="after-sale-reason">{{ currentAfterSale?.reason || '-' }}</div>
        </el-form-item>
        <el-alert
          v-if="currentAfterSale?.nextActionHint"
          :title="currentAfterSale.nextActionHint"
          :description="currentAfterSale.nextActionDeadline ? `${formatDateTime(currentAfterSale.nextActionDeadline)} 截止；超时只升级为平台优先介入，不会自动退款。` : ''"
          :type="currentAfterSale.nextActionOverdue ? 'error' : 'warning'"
          :closable="false"
          show-icon
          class="after-sale-action-alert"
        />
        <el-form-item v-if="afterSaleProofUrls(currentAfterSale).length" label="图片凭证">
          <div class="after-sale-proof-grid">
            <el-image
              v-for="url in afterSaleProofUrls(currentAfterSale)"
              :key="url"
              :src="url"
              :preview-src-list="afterSaleProofUrls(currentAfterSale)"
              fit="cover"
              preview-teleported
            />
          </div>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input v-model="auditForm.auditRemark" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="auditForm.status === 1 ? '可填写处理说明' : '拒绝或关闭必须说明具体原因，会员端会直接展示'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button :type="auditForm.status === 1 ? 'success' : auditForm.status === 2 ? 'danger' : 'warning'" @click="submitAudit">
          确认{{ auditActionLabel }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exchangeShipmentDialogVisible" title="发出换货商品" width="520px" destroy-on-close>
      <el-alert
        title="仅发出原订单同规格商品"
        description="本次会扣减对应商品和规格的可售库存，但不会增加销量，也不会退款或重算原订单奖金。客户寄回的商品不会自动计入可售库存。"
        type="warning"
        :closable="false"
        show-icon
        class="after-sale-action-alert"
      />
      <el-form :model="exchangeShipmentForm" label-width="92px">
        <el-form-item label="售后号">
          <el-input :model-value="currentAfterSale?.afterSaleNo" disabled />
        </el-form-item>
        <el-form-item label="换货数量">
          <span>{{ Number(currentAfterSale?.refundQuantity || 0) }} 件</span>
        </el-form-item>
        <el-form-item label="物流公司" required>
          <el-select v-model="exchangeShipmentForm.deliveryCompany" filterable clearable placeholder="请选择物流公司" style="width: 100%">
            <el-option v-for="company in logisticsCompanyOptions" :key="company" :label="company" :value="company" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model.trim="exchangeShipmentForm.deliveryNo" maxlength="64" autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exchangeShipmentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exchangeShipmentLoading" @click="submitExchangeShipment">确认发出</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manualRefundDialogVisible" title="后台退款" width="720px" destroy-on-close>
      <el-alert
        title="前台售后期限已结束，后台退款会写入售后、财务和奖金冲销记录。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="2" border class="manual-refund-summary">
        <el-descriptions-item label="订单号">{{ currentOrder?.order?.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatDateTime(currentOrder?.order?.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="manualRefundForm" label-width="110px" class="manual-refund-form">
        <el-form-item label="退款方式">
          <el-radio-group v-model="manualRefundForm.refundMode">
            <el-radio value="QUANTITY">按盒数比例退款</el-radio>
            <el-radio value="AMOUNT">按后台填写金额退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="退款商品">
          <el-table :data="currentOrder?.items || []" border size="small" class="manual-refund-items">
            <el-table-column label="商品 / 规格" min-width="230">
              <template #default="{ row }">
                <div>{{ row.productName || '商品' }}</div>
                <div class="sub">{{ formatProductSpec(row) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="已购盒数" width="90" align="center">
              <template #default="{ row }">{{ Number(row.quantity || 0) }}</template>
            </el-table-column>
            <el-table-column label="本次退款盒数" width="150" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="manualRefundForm.items[row.id]"
                  :min="0"
                  :max="remainingRefundQuantity(currentOrder, row)"
                  :step="1"
                  step-strictly
                  controls-position="right"
                  size="small"
                />
                <div class="remaining-tip">可退 {{ remainingRefundQuantity(currentOrder, row) }} 盒</div>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item v-if="manualRefundForm.refundMode === 'QUANTITY'" label="按盒数预计退款">
          <span class="manual-refund-amount">¥{{ money(manualRefundEstimate) }}</span>
          <div class="field-help">按本次选择的盒数占商品实付金额的比例计算，整单退完时补齐尾差。</div>
        </el-form-item>
        <el-form-item v-else label="商品退款金额" required>
          <el-input-number v-model="manualRefundForm.productRefundAmount" :min="0.01" :max="Math.max(0.01, manualRefundRemainingAmount)" :precision="2" :step="0.01" controls-position="right" />
          <div class="field-help">金额仅限商品款，本单剩余最多可退 ¥{{ money(manualRefundRemainingAmount) }}；仍需选择本次涉及的盒数。</div>
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="manualRefundForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请填写后台退款原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualRefundDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="manualRefundLoading" @click="submitManualRefund">确认退款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bonusDialogVisible" title="订单奖金去向" width="1180px" destroy-on-close>
      <div v-loading="bonusLoading">
        <el-descriptions :column="4" border class="bonus-summary">
          <el-descriptions-item label="订单编号">{{ bonusOrder.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单登录账号">{{ bonusOrder.memberAccount || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单总金额">
            ¥{{ money(bonusFinance.finance?.payAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="奖金总拨出">
            <span :class="{ danger: payoutExceeded(bonusFinance.finance?.payAmount, bonusFinance.finance?.bonusAmount) }">
              ¥{{ money(bonusFinance.finance?.bonusAmount) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="bonusTrace.statusName"
          :title="`当前状态：${bonusTrace.statusName}`"
          :description="bonusTrace.explanation"
          :type="bonusTraceAlertType"
          :closable="false"
          show-icon
          class="bonus-alert"
        />

        <el-alert
          v-if="payoutExceeded(bonusFinance.finance?.payAmount, bonusFinance.finance?.bonusAmount)"
          title="风险提醒：该订单奖金总拨出已经超过订单总金额"
          type="error"
          :closable="false"
          show-icon
          class="bonus-alert"
        />

        <div class="bonus-trace-metrics">
          <div><span>程序计算金额</span><strong>¥{{ money(bonusTrace.calculatedAmount) }}</strong></div>
          <div><span>待结算</span><strong>¥{{ money(bonusTrace.pendingAmount) }}</strong></div>
          <div><span>已结算净额</span><strong>¥{{ money(bonusTrace.settledNetAmount) }}</strong></div>
          <div><span>累计冲减/追回</span><strong>¥{{ money(bonusTrace.clawbackAmount) }}</strong></div>
          <div><span>当前有效净额</span><strong class="primary-value">¥{{ money(bonusTrace.currentNetAmount) }}</strong></div>
          <div><span>待追回</span><strong :class="{ danger: Number(bonusTrace.debtAmount || 0) > 0 }">¥{{ money(bonusTrace.debtAmount) }}</strong></div>
        </div>

        <section class="bonus-trace-section">
          <div class="bonus-trace-title">
            <div><h3>实际奖金记录</h3><p>保留原“奖金去向”的真实记录；每一行都是客户奖金程序实际生成的收款结果。</p></div>
            <el-tag type="success" effect="plain">现有实际记录 {{ bonusTrace.actualRecords?.length || 0 }} 条</el-tag>
          </div>
        <el-table :data="bonusTrace.actualRecords || []" style="width: 100%" empty-text="该订单暂未产生实际奖金记录">
          <el-table-column prop="recordNo" label="奖金记录号" min-width="180" />
          <el-table-column prop="agentMemberAccount" label="获奖登录账号" width="145" />
          <el-table-column prop="agentName" label="获奖会员" width="130" />
          <el-table-column label="奖金类型" width="180">
            <template #default="{ row }">{{ bonusTypeName(row) }}</template>
          </el-table-column>
          <el-table-column prop="commissionLevel" label="关系深度" width="95" />
          <el-table-column label="奖金比例" width="100">
            <template #default="{ row }">{{ percent(row.commissionRate) }}</template>
          </el-table-column>
          <el-table-column label="奖金金额" width="120">
            <template #default="{ row }">¥{{ money(row.commissionAmount) }}</template>
          </el-table-column>
          <el-table-column prop="statusName" label="奖金状态" width="100" />
          <el-table-column label="产生时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        </section>

        <section class="bonus-trace-section">
          <div class="bonus-trace-title">
            <div><h3>余额入账与扣回流水</h3><p>只有真实改变会员余额的动作才会出现在这里，可核对流水号和变动前后余额。</p></div>
          </div>
          <el-table :data="bonusTrace.assetFlows || []" style="width:100%" empty-text="该订单尚无奖金余额变动">
            <el-table-column prop="flowNo" label="资产流水号" min-width="190" />
            <el-table-column prop="recordNo" label="奖金记录号" min-width="180" />
            <el-table-column prop="memberAccount" label="会员登录账号" width="145" />
            <el-table-column prop="memberName" label="会员昵称" width="125" />
            <el-table-column prop="actionName" label="动作" width="100">
              <template #default="{ row }"><el-tag :type="row.action === 'CLAWBACK' ? 'warning' : 'success'">{{ row.actionName }}</el-tag></template>
            </el-table-column>
            <el-table-column label="金额" width="110"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="变动前" width="110"><template #default="{ row }">¥{{ money(row.balanceBefore) }}</template></el-table-column>
            <el-table-column label="变动后" width="110"><template #default="{ row }">¥{{ money(row.balanceAfter) }}</template></el-table-column>
            <el-table-column label="发生时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
          </el-table>
        </section>

        <section class="bonus-trace-section">
          <div class="bonus-trace-title">
            <div><h3>退款冲销、欠款抵扣与待追回</h3><p>区分应追回、已经扣回、历史欠款抵扣和仍待追回，避免只看奖金记录误判实际净额。</p></div>
          </div>
          <el-table :data="bonusTrace.clawbacks || []" style="width:100%" empty-text="该订单没有奖金退款冲销">
            <el-table-column prop="recordNo" label="奖金记录号" min-width="180" />
            <el-table-column prop="memberAccount" label="会员登录账号" width="145" />
            <el-table-column prop="typeName" label="追回方式" min-width="150" />
            <el-table-column label="应追回" width="105"><template #default="{ row }">¥{{ money(row.clawbackAmount) }}</template></el-table-column>
            <el-table-column label="已冲减" width="105"><template #default="{ row }">¥{{ money(row.deductedAmount) }}</template></el-table-column>
            <el-table-column label="待追回" width="105"><template #default="{ row }"><span :class="{ danger: Number(row.debtAmount || 0) > 0 }">¥{{ money(row.debtAmount) }}</span></template></el-table-column>
            <el-table-column prop="statusName" label="状态" width="100" />
            <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
            <el-table-column label="发生时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
          </el-table>
        </section>

        <el-collapse v-model="bonusAuditSections" class="bonus-audit-collapse">
          <el-collapse-item name="trace-evidence">
            <template #title>
              <div class="bonus-audit-collapse-title">
                <strong>查看审计详情</strong>
                <span>支付、关系冻结、程序版本与计算证据</span>
              </div>
            </template>

            <section class="bonus-trace-section bonus-audit-section">
              <div class="bonus-trace-title">
                <div><h3>全链路时间线</h3><p>按真实发生时间串联支付、关系冻结、计算、入账、退款和冲销。</p></div>
              </div>
              <el-timeline v-if="bonusTrace.timeline?.length" class="bonus-timeline">
                <el-timeline-item
                  v-for="item in bonusTrace.timeline"
                  :key="`${item.code}-${item.time}-${item.description}`"
                  :timestamp="formatDateTime(item.time)"
                  :type="traceEventType(item.status)"
                  placement="top"
                >
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.description }}</p>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="该订单尚未进入奖金链路" :image-size="72" />
            </section>

            <section class="bonus-trace-section bonus-audit-section">
              <div class="bonus-trace-title">
                <div><h3>计算依据与冻结关系</h3><p>这里只展示订单支付时保存的证据，不按当前上下级关系倒推。</p></div>
              </div>
              <el-descriptions :column="4" border class="bonus-program-summary">
                <el-descriptions-item label="客户奖金程序">{{ bonusTrace.ruleVersionName || '未进入奖金程序' }}</el-descriptions-item>
                <el-descriptions-item label="程序版本">{{ bonusTrace.ruleVersionNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="计算方式">{{ bonusTrace.calculationTaskStatusName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="冻结关系">{{ bonusTrace.relationCount || 0 }} 层</el-descriptions-item>
              </el-descriptions>
              <el-table :data="bonusTrace.relationChain || []" style="width:100%" empty-text="该订单没有冻结推广关系">
                <el-table-column prop="relationLevel" label="关系深度" width="100" />
                <el-table-column prop="memberAccount" label="会员登录账号" min-width="150" />
                <el-table-column prop="memberName" label="会员昵称" min-width="140" />
                <el-table-column prop="relationPath" label="冻结关系路径" min-width="220" show-overflow-tooltip />
                <el-table-column label="冻结时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.snapshotTime) }}</template>
                </el-table-column>
              </el-table>
              <el-table v-if="bonusTrace.calculationEvidence?.length" :data="bonusTrace.calculationEvidence" class="bonus-evidence-table" style="width:100%">
                <el-table-column prop="id" label="计算证据号" width="120" />
                <el-table-column label="计算PV" width="130"><template #default="{ row }">{{ money(row.totalPv) }}</template></el-table-column>
                <el-table-column label="计算奖金" width="140"><template #default="{ row }">¥{{ money(row.totalBonus) }}</template></el-table-column>
                <el-table-column prop="riskStatusName" label="通用资金校验" width="130" />
                <el-table-column label="留存时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
              </el-table>
            </section>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  auditShopAfterSale,
  cancelShopOrder,
  confirmShopAfterSaleReturnReceived,
  downloadOrderShipmentImportTemplate,
  downloadOrderShipmentTemplate,
  exportShopOrders,
  getAdminOrderWorkSummary,
  getAdminOrderTracking,
  getShopTradeDetail,
  importOrderShipments,
  listShopOrders,
  manualRefundShopOrder,
  shipShopOrder,
  shipShopAfterSaleExchangeReplacement,
  updateShopOrderServiceRemark,
} from '@/api/shop'
import { getOrderFinance } from '@/api/audit'
import { formatProductSpec } from '@/utils/productSpec'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { useAppStore } from '@/store'
import { formatDateTime } from '@/utils/dateTime'
import { logisticsCompanyOptions } from '@/utils/logisticsCompanies'
import { customerBonusName } from '@/utils/customerBonus'

const appStore = useAppStore()
const route = useRoute()
const isMerchantUser = computed(() => Boolean(appStore.userInfo?.merchantId))
const orderLoading = ref(false)
const exportLoading = ref(false)
const templateLoading = ref(false)
const importTemplateLoading = ref(false)
const importLoading = ref(false)
const orders = ref([])
const merchantFulfillmentAllowed = computed(() => !isMerchantUser.value
  || !orders.value.length
  || orders.value.some((row) => row.merchantFulfillmentAllowed !== false))
const merchantScopeTip = computed(() => merchantFulfillmentAllowed.value
  ? '这里只显示本商户的履约子订单。您可以发货、填写客服备注并处理正常客户售后；联合支付汇总、平台取消、人工退款和团队奖金由平台管理。'
  : '这里只显示本商户的履约子订单。当前履约已由平台接管或冻结，您仍可查看历史订单和填写客服备注，但不能发货或处理售后。')
const orderWorkSummary = ref({ pendingShipment: 0, afterSale: 0 })
const orderStateOptions = [
  { label: '全部', value: '' },
  { label: '待付款', value: 'PENDING_PAYMENT' },
  { label: '待发货', value: 'PENDING_SHIPMENT' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '待售后', value: 'AFTER_SALE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已退款', value: 'REFUNDED' },
]
const initialOrderState = orderStateOptions.some((item) => item.value === route.query.orderState)
  ? String(route.query.orderState)
  : ''
const query = ref({ keyword: '', orderState: initialOrderState })
const pagination = ref({ page: 1, size: 10, total: 0 })
const shipDialogVisible = ref(false)
const shipmentResultVisible = ref(false)
const shipmentResult = ref({ success: false, totalRows: 0, shippedCount: 0, skippedCount: 0, failedCount: 0, errors: [] })
const auditDialogVisible = ref(false)
const manualRefundDialogVisible = ref(false)
const manualRefundLoading = ref(false)
const exchangeShipmentDialogVisible = ref(false)
const exchangeShipmentLoading = ref(false)
const serviceRemarkDialogVisible = ref(false)
const serviceRemarkLoading = ref(false)
const serviceRemarkForm = ref('')
const bonusDialogVisible = ref(false)
const bonusLoading = ref(false)
const bonusFinance = ref({})
const bonusOrder = ref({ orderNo: '', memberAccount: '' })
const bonusAuditSections = ref([])
const bonusTrace = computed(() => bonusFinance.value?.bonusTrace || {
  actualRecords: bonusFinance.value?.bonusFlows || [],
  relationChain: [],
  calculationEvidence: [],
  assetFlows: [],
  clawbacks: [],
  timeline: [],
})
const bonusTraceAlertType = computed(() => {
  if (['CALCULATION_FAILED', 'DATA_CONFLICT', 'DEBT_PENDING'].includes(bonusTrace.value?.status)) return 'error'
  if (['PENDING_SETTLEMENT', 'PARTIALLY_SETTLED', 'REFUND_ADJUSTED', 'DEBT_OFFSET'].includes(bonusTrace.value?.status)) return 'warning'
  if (['SETTLED'].includes(bonusTrace.value?.status)) return 'success'
  return 'info'
})
const currentOrder = ref(null)
const tradeDetailVisible = ref(false)
const tradeDetailLoading = ref(false)
const tradeDetail = ref({ trade: null, childOrders: [], childCount: 0, refundedAmount: 0 })
const trackingVisible = ref(false)
const trackingLoading = ref(false)
const trackingRows = ref([])
const currentAfterSale = ref(null)
const shipForm = ref({ deliveryCompany: '', deliveryNo: '', shipmentQuantity: 1 })
const exchangeShipmentForm = ref({ deliveryCompany: '', deliveryNo: '' })
const auditForm = ref({ status: 1, auditRemark: '', auditUserId: 1, auditUserName: 'admin' })
const manualRefundForm = ref({ refundMode: 'QUANTITY', productRefundAmount: 0, items: {}, reason: '' })
const currentOperator = computed(() => ({
  id: appStore.userInfo?.id || 1,
  name: appStore.userInfo?.nickname || appStore.userInfo?.username || '管理员',
}))
const orderSearchFeedback = ref('')
const orderEmptyText = ref('暂无订单记录')
const { markSearchApplied: markOrderSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => {
    pagination.value.page = 1
    fetchOrders()
  },
)
const money = (value) => Number(value || 0).toFixed(2)
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const payoutExceeded = (orderAmount, bonusAmount) => Number(bonusAmount || 0) > Number(orderAmount || 0)
const bonusTypeName = (row) => customerBonusName(row)
const traceEventType = (status) => ({ success: 'success', warning: 'warning', danger: 'danger', info: 'info' }[status] || 'info')
const afterSaleStatus = (status, applyType) => {
  if (Number(applyType) === 3 && Number(status) === 1) return '换货完成'
  return ({ 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待客户寄回', 5: '待商家收货', 6: '退款处理中', 7: '待换货发出', 8: '换货已发出' }[status] || '处理中')
}
const afterSaleTag = (status) => ({ 0: 'warning', 1: 'success', 2: 'info', 3: 'warning', 4: 'warning', 5: 'primary', 6: 'warning', 7: 'warning', 8: 'primary' }[status] || 'info')
const tradeStatusLabel = (status) => ({ 0: '待付款', 1: '已支付', 4: '已关闭' }[Number(status)] || '未知')
const payTypeLabel = (payType) => ({ BALANCE: '余额支付', ALIPAY: '支付宝', WECHAT: '微信支付' }[payType] || payType || '-')
const afterSaleProofUrls = (sale) => {
  if (!sale?.memberId) return []
  try {
    const filenames = JSON.parse(sale.proofImages || '[]')
    return Array.isArray(filenames)
      ? filenames.filter((filename) => typeof filename === 'string' && filename)
        .map((filename) => `/api/shop/admin/after-sales/proofs/${sale.memberId}/${encodeURIComponent(filename)}`)
      : []
  } catch {
    return []
  }
}
const hasPendingAfterSale = (row) => (row?.afterSales || []).some((item) => [0, 4, 5, 6, 7, 8].includes(Number(item.status)))
const activeAfterSale = (row) => (row?.afterSales || []).find((item) => [0, 4, 5, 6, 7, 8].includes(Number(item.status)))
const approvedAfterSales = (row) => (row?.afterSales || []).filter((item) => [1, 2].includes(Number(item.applyType)) && Number(item.status) === 1)
const hasApprovedRefund = (row) => approvedAfterSales(row).length > 0
const approvedRefundAmount = (row) => approvedAfterSales(row)
  .reduce((sum, item) => sum + Number(item.refundAmount || 0), 0)
const approvedRefundQuantity = (row) => approvedAfterSales(row)
  .reduce((sum, item) => sum + Number(item.refundQuantity || 0), 0)
const orderStateCount = (state) => ({
  PENDING_SHIPMENT: Number(orderWorkSummary.value.pendingShipment || 0),
  AFTER_SALE: Number(orderWorkSummary.value.afterSale || 0),
}[state] || 0)
const orderDisplayStatus = (row) => {
  return ({ 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭' }[row?.order?.status] || '处理中')
}
const orderDisplayTag = (row) => {
  return ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info' }[row?.order?.status] || 'info')
}
const shipmentRows = (row) => {
  if (row?.shipments?.length) return row.shipments
  if (row?.order?.deliveryNo) {
    return [{
      deliveryCompany: row.order.deliveryCompany,
      deliveryNo: row.order.deliveryNo,
      shipmentQuantity: (row.items || []).reduce((sum, item) => sum + Number(item?.quantity || 0), 0),
      deliveryTime: row.order.deliveryTime,
    }]
  }
  return []
}
const openTracking = async (row) => {
  trackingVisible.value = true
  trackingLoading.value = true
  trackingRows.value = []
  try {
    trackingRows.value = (await getAdminOrderTracking(row.order.id)).data || []
  } finally {
    trackingLoading.value = false
  }
}
const orderedQuantity = (row) => (row?.items || []).reduce((sum, item) => sum + Number(item?.quantity || 0), 0)
const isFullRefund = (row) => hasApprovedRefund(row) && (
  (orderedQuantity(row) > 0 && approvedRefundQuantity(row) >= orderedQuantity(row))
  || (Number(row?.order?.payAmount || 0) > 0
    && approvedRefundAmount(row) >= Number(row.order.payAmount) - 0.01)
)
const refundResultLabel = (row) => isFullRefund(row) ? '全额退款' : '部分退款'
const shippedQuantity = (row) => shipmentRows(row).reduce((sum, item) => sum + Number(item?.shipmentQuantity || 0), 0)
const remainingShipmentQuantity = (row) => Math.max(0, orderedQuantity(row) - shippedQuantity(row))
const canMerchantFulfill = (row) => !isMerchantUser.value || row?.merchantFulfillmentAllowed !== false
const canShipOrder = (row) => canMerchantFulfill(row) && !hasPendingAfterSale(row)
  && [1, 2].includes(Number(row?.order?.status))
  && remainingShipmentQuantity(row) > 0
const canCancelAdminOrder = (row) => !isMerchantUser.value && !hasPendingAfterSale(row)
  && [0, 1].includes(Number(row?.order?.status))
const afterSaleDeadline = (row) => {
  const configured = Date.parse(String(row?.afterSaleDeadline || '').replace(' ', 'T'))
  return Number.isFinite(configured) ? configured : Number.NaN
}
const isCustomerAfterSaleClosed = (row) => row?.afterSaleSelfServiceEnabled === false
  || (Number.isFinite(afterSaleDeadline(row)) && Date.now() >= afterSaleDeadline(row))
const canManualRefund = (row) => !isMerchantUser.value && !hasPendingAfterSale(row)
  && [1, 2, 3].includes(Number(row?.order?.status))
  && isCustomerAfterSaleClosed(row)
const refundedQuantity = (row, itemId) => (row?.afterSales || [])
  .filter((sale) => Number(sale.applyType) === 3
    ? [0, 4, 5, 7, 8].includes(Number(sale.status))
    : [0, 1, 4, 5, 6].includes(Number(sale.status)))
  .flatMap((sale) => sale.items || [])
  .filter((item) => item.orderItemId === itemId)
  .reduce((sum, item) => sum + Number(item.refundQuantity || 0), 0)
const remainingRefundQuantity = (row, item) => Math.max(0, Number(item?.quantity || 0) - refundedQuantity(row, item?.id))
const selectedRefundQuantity = computed(() => Object.values(manualRefundForm.value.items || {})
  .reduce((sum, quantity) => sum + Math.max(0, Number(quantity || 0)), 0))
const manualRefundRemainingAmount = computed(() => {
  if (!currentOrder.value) return 0
  const productBase = Math.max(0, Number(currentOrder.value.order?.totalAmount || 0) - Number(currentOrder.value.order?.discountAmount || 0))
  const approved = (currentOrder.value.afterSales || [])
    .filter((sale) => [1, 2].includes(Number(sale.applyType)) && [1, 6].includes(Number(sale.status)))
    .reduce((sum, sale) => sum + Number(sale.productRefundAmount || 0), 0)
  return Math.max(0, productBase - approved)
})
const manualRefundEstimate = computed(() => {
  if (!currentOrder.value || manualRefundForm.value.refundMode !== 'QUANTITY') return 0
  const productBase = Math.max(0, Number(currentOrder.value.order?.totalAmount || 0) - Number(currentOrder.value.order?.discountAmount || 0))
  const grossTotal = (currentOrder.value.items || []).reduce((sum, item) => sum + Number(item.totalAmount || 0), 0)
  if (!productBase || !grossTotal || !selectedRefundQuantity.value) return 0
  const selectedGross = (currentOrder.value.items || []).reduce((sum, item) => {
    const quantity = Math.min(remainingRefundQuantity(currentOrder.value, item), Number(manualRefundForm.value.items?.[item.id] || 0))
    return sum + Number(item.totalAmount || 0) * quantity / Math.max(1, Number(item.quantity || 0))
  }, 0)
  const totalRemaining = (currentOrder.value.items || []).reduce((sum, item) => sum + remainingRefundQuantity(currentOrder.value, item), 0)
  const remainingAmount = manualRefundRemainingAmount.value
  return selectedRefundQuantity.value === totalRemaining
    ? remainingAmount
    : Math.min(remainingAmount, selectedGross * productBase / grossTotal)
})

const fetchOrders = async () => {
  const validation = validateSearchKeyword(query.value.keyword, { label: '订单关键词' })
  if (!validation.valid) {
    orders.value = []
    pagination.value.total = 0
    orderSearchFeedback.value = validation.message
    orderEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.value.keyword = validation.keyword
  markOrderSearchApplied(validation.keyword)
  orderSearchFeedback.value = ''
  orderEmptyText.value = validation.keyword
    ? `未找到与“${validation.keyword}”匹配的订单`
    : '暂无订单记录'
  orderLoading.value = true
  try {
    const res = await listShopOrders({
      ...query.value,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    orders.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally {
    orderLoading.value = false
  }
}

const applyWorkSummary = (summary, refreshQueue = false) => {
  const previous = orderStateCount(query.value.orderState)
  orderWorkSummary.value = {
    pendingShipment: Number(summary?.pendingShipment || 0),
    afterSale: Number(summary?.afterSale || 0),
  }
  if (refreshQueue && ['PENDING_SHIPMENT', 'AFTER_SALE'].includes(query.value.orderState)
    && previous !== orderStateCount(query.value.orderState)) {
    fetchOrders()
  }
}

const fetchWorkSummary = async () => {
  try {
    const res = await getAdminOrderWorkSummary()
    applyWorkSummary(res.data)
  } catch {
    // 数字提醒读取失败不阻断订单处理，下一轮自动刷新会再次尝试。
  }
}

const handleWorkSummaryUpdate = (event) => applyWorkSummary(event.detail, true)
const handleRealtimeOrderChange = () => fetchOrders()

const handleOrderSearch = () => {
  pagination.value.page = 1
  fetchOrders()
}

const changeOrderState = (orderState) => {
  if (query.value.orderState === orderState) return
  query.value.orderState = orderState
  pagination.value.page = 1
  fetchOrders()
}

const resetOrderQuery = () => {
  query.value.keyword = ''
  pagination.value.page = 1
  fetchOrders()
}

const downloadBlobResponse = (response, fallbackName) => {
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
  const disposition = response.headers?.['content-disposition'] || ''
  const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  let filename = fallbackName
  if (encodedName) {
    try { filename = decodeURIComponent(encodedName) } catch (e) { filename = fallbackName }
  }
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

const handleExportOrders = async () => {
  exportLoading.value = true
  try {
    const response = await exportShopOrders(query.value)
    downloadBlobResponse(response, '商城订单.xlsx')
    ElMessage.success('订单表格已导出')
  } finally {
    exportLoading.value = false
  }
}

const handleDownloadShipmentTemplate = async () => {
  templateLoading.value = true
  try {
    const response = await downloadOrderShipmentTemplate({ keyword: query.value.keyword })
    downloadBlobResponse(response, '待发货订单物流回填.xlsx')
    ElMessage.success('发货表已下载；拆单可复制订单行，合箱可共用物流单号')
  } finally {
    templateLoading.value = false
  }
}

const handleDownloadShipmentImportTemplate = async () => {
  importTemplateLoading.value = true
  try {
    const response = await downloadOrderShipmentImportTemplate()
    downloadBlobResponse(response, '物流发货导入模板.xlsx')
    ElMessage.success('物流发货导入模板已下载，请按“填写说明”填写后导入')
  } finally {
    importTemplateLoading.value = false
  }
}

const handleShipmentImport = async ({ file }) => {
  try {
    await ElMessageBox.confirm(
      `确认导入“${file.name}”吗？系统只读取订单号、物流公司、物流单号和发货数量。`,
      '导入物流并发货',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' },
    )
  } catch (action) {
    return
  }
  importLoading.value = true
  try {
    const response = await importOrderShipments(file)
    shipmentResult.value = response.data || {}
    shipmentResultVisible.value = true
    if (Number(shipmentResult.value.shippedCount || 0) > 0) {
      await Promise.all([fetchOrders(), fetchWorkSummary()])
    }
  } finally {
    importLoading.value = false
  }
}

const openShip = (row) => {
  currentOrder.value = row
  shipForm.value = {
    deliveryCompany: '',
    deliveryNo: '',
    shipmentQuantity: remainingShipmentQuantity(row),
  }
  shipDialogVisible.value = true
}

const openServiceRemark = (row) => {
  currentOrder.value = row
  serviceRemarkForm.value = row?.serviceRemark || ''
  serviceRemarkDialogVisible.value = true
}

const openTradeDetail = async (tradeId) => {
  if (!tradeId || isMerchantUser.value) return
  tradeDetailVisible.value = true
  tradeDetailLoading.value = true
  tradeDetail.value = { trade: null, childOrders: [], childCount: 0, refundedAmount: 0 }
  try {
    const response = await getShopTradeDetail(tradeId)
    tradeDetail.value = response.data || tradeDetail.value
  } finally {
    tradeDetailLoading.value = false
  }
}

const submitServiceRemark = async () => {
  if (!currentOrder.value?.order?.id) return
  const serviceRemark = serviceRemarkForm.value.trim()
  if (serviceRemark.length > 500) {
    ElMessage.warning('客服备注不能超过500个字')
    return
  }
  serviceRemarkLoading.value = true
  try {
    await updateShopOrderServiceRemark(currentOrder.value.order.id, serviceRemark)
    currentOrder.value.serviceRemark = serviceRemark
    ElMessage.success(serviceRemark ? '客服备注已保存' : '客服备注已清除')
    serviceRemarkDialogVisible.value = false
  } finally {
    serviceRemarkLoading.value = false
  }
}

const cancelAdminOrder = async (row) => {
  const orderNo = row?.order?.orderNo || '-'
  const paid = Number(row?.order?.status) === 1
  try {
    await ElMessageBox.confirm(
      paid
        ? `确认取消待发货订单“${orderNo}”吗？系统会原路全额退款、关闭订单并恢复库存，不能恢复。`
        : `确认取消订单“${orderNo}”吗？取消后订单将关闭，预占库存会回库，不能恢复。`,
      paid ? '取消并退款' : '取消订单',
      { type: 'warning', confirmButtonText: paid ? '确认取消并退款' : '确认取消', cancelButtonText: '暂不取消' },
    )
  } catch {
    return
  }
  await cancelShopOrder(row.order.id)
  ElMessage.success(paid ? '订单已取消并完成退款，库存已回库' : '订单已取消，库存已回库')
  await Promise.all([fetchOrders(), fetchWorkSummary()])
}

const openBonusFlows = async (orderId, orderNo, memberAccount) => {
  if (!orderId) return ElMessage.warning('订单信息不完整，无法查询奖金去向')
  bonusOrder.value = { orderNo, memberAccount }
  bonusFinance.value = {}
  bonusAuditSections.value = []
  bonusDialogVisible.value = true
  bonusLoading.value = true
  try {
    const res = await getOrderFinance(orderId)
    bonusFinance.value = res.data || {}
  } finally {
    bonusLoading.value = false
  }
}

const handleAfterSaleCommand = (status, sale) => {
  if (!sale || ![1, 2, 3].includes(Number(status))) return
  openAudit(sale, Number(status))
}

const handleOrderMoreCommand = (command, row) => {
  if (command === 'BONUS') {
    openBonusFlows(row.order?.id, row.order?.orderNo, row.memberAccount)
  } else if (command === 'CANCEL') {
    cancelAdminOrder(row)
  } else if (command === 'REFUND') {
    openManualRefund(row)
  }
}

const submitShip = async () => {
  if (!shipForm.value.deliveryCompany || !shipForm.value.deliveryNo || !Number.isInteger(shipForm.value.shipmentQuantity) || shipForm.value.shipmentQuantity <= 0) {
    ElMessage.warning('请填写物流公司、物流单号和正确的发货数量')
    return
  }
  await shipShopOrder(currentOrder.value.order.id, shipForm.value)
  ElMessage.success(currentOrder.value.order.status === 2 ? '物流包裹已添加' : '发货成功')
  shipDialogVisible.value = false
  await Promise.all([fetchOrders(), fetchWorkSummary()])
}

const openManualRefund = (row) => {
  currentOrder.value = row
  manualRefundForm.value = {
    refundMode: 'QUANTITY',
    productRefundAmount: 0,
    items: Object.fromEntries((row.items || []).map((item) => [item.id, 0])),
    reason: '客户自助售后入口已关闭，后台根据客服协商处理',
  }
  manualRefundDialogVisible.value = true
}

const submitManualRefund = async () => {
  if (!currentOrder.value?.order?.id) return
  const items = Object.entries(manualRefundForm.value.items || {})
    .map(([orderItemId, quantity]) => ({ orderItemId: Number(orderItemId), quantity: Math.trunc(Number(quantity || 0)) }))
    .filter((item) => item.quantity > 0)
  if (!items.length) {
    ElMessage.warning('请选择本次退款涉及的商品盒数')
    return
  }
  if (manualRefundForm.value.refundMode === 'AMOUNT' && Number(manualRefundForm.value.productRefundAmount || 0) <= 0) {
    ElMessage.warning('请输入大于0的商品退款金额')
    return
  }
  if (manualRefundForm.value.refundMode === 'AMOUNT'
    && Number(manualRefundForm.value.productRefundAmount || 0) > manualRefundRemainingAmount.value + 0.001) {
    ElMessage.warning(`商品退款金额不能超过剩余可退金额 ¥${money(manualRefundRemainingAmount.value)}`)
    return
  }
  manualRefundLoading.value = true
  try {
    await manualRefundShopOrder(currentOrder.value.order.id, {
      refundMode: manualRefundForm.value.refundMode,
      productRefundAmount: manualRefundForm.value.refundMode === 'AMOUNT'
        ? Number(manualRefundForm.value.productRefundAmount)
        : null,
      items,
      reason: manualRefundForm.value.reason?.trim() || '后台超期退款',
      applyType: 1,
      operatorId: currentOperator.value.id,
      operatorName: currentOperator.value.name,
    })
    ElMessage.success('后台退款已登记并完成账务冲销')
    manualRefundDialogVisible.value = false
    await Promise.all([fetchOrders(), fetchWorkSummary()])
  } finally {
    manualRefundLoading.value = false
  }
}

const openAudit = (row, status) => {
  currentAfterSale.value = row
  auditForm.value = {
    status,
    auditRemark: '',
    auditUserId: currentOperator.value.id,
    auditUserName: currentOperator.value.name,
  }
  auditDialogVisible.value = true
}

const auditDialogTitle = computed(() => ({ 1: '通过售后', 2: '拒绝售后', 3: '关闭售后申请' }[auditForm.value.status] || '处理售后'))
const auditActionLabel = computed(() => ({ 1: '通过', 2: '拒绝', 3: '关闭售后' }[auditForm.value.status] || '提交'))

const submitAudit = async () => {
  const actionStatus = auditForm.value.status
  if ([2, 3].includes(actionStatus) && !auditForm.value.auditRemark.trim()) {
    ElMessage.warning(actionStatus === 2 ? '请填写拒绝原因' : '请填写关闭原因')
    return
  }
  const exchange = Number(currentAfterSale.value?.applyType) === 3
  const actionText = ({ 1: '通过该售后申请', 2: '拒绝该售后申请', 3: '关闭该售后申请' })[actionStatus] || '提交本次售后处理'
  const approvedImpact = exchange
    ? '，客户需要寄回原商品，确认退件后再发出同规格商品；不会退款或重算奖金'
    : '，并可能立即执行退款和账务冲销'
  await ElMessageBox.confirm(
    `确认${actionText}？该操作会改变订单售后状态${actionStatus === 1 ? approvedImpact : ''}。`,
    '确认售后处理',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '返回检查' },
  )
  await auditShopAfterSale(currentAfterSale.value.id, auditForm.value)
  ElMessage.success(actionStatus === 3 ? '售后申请已关闭' : '审核完成')
  auditDialogVisible.value = false
  await Promise.all([fetchOrders(), fetchWorkSummary()])
}

const confirmReturnReceived = async (sale) => {
  const retrying = Number(sale?.status) === 6
  const exchange = Number(sale?.applyType) === 3
  const { value: auditRemark } = await ElMessageBox.prompt(
    retrying
      ? '将使用同一售后单号重新查询并执行渠道退款，不会重复处理本地库存和奖金。请填写本次处理备注。'
      : exchange
        ? '确认收到客户寄回的商品后，售后会进入“待换货发出”；此步不退款、不回补可售库存，也不重算奖金。请填写验收备注。'
        : '确认已收到客户寄回的商品，并执行退款、库存和财务处理。请填写验收备注。',
    retrying ? '重试渠道退款' : exchange ? '确认收到换货退件' : '确认收货并退款',
    {
      type: 'warning',
      inputValue: retrying ? '渠道退款重试' : exchange ? '商家确认收到换货退件，商品验收完成' : '商家确认收到退货，商品验收无误',
      inputPlaceholder: '例如：外包装完整，商品数量核对无误',
      inputValidator: (value) => Boolean(value?.trim()) || '请填写本次处理备注',
      confirmButtonText: retrying ? '确认重试' : exchange ? '确认收到退件' : '确认收货并退款',
      cancelButtonText: '取消',
    },
  )
  await confirmShopAfterSaleReturnReceived(sale.id, {
    auditRemark: auditRemark.trim(),
    auditUserId: currentOperator.value.id,
    auditUserName: currentOperator.value.name,
  })
  ElMessage.success(retrying ? '渠道退款已恢复完成' : exchange ? '已确认退件，等待发出换货商品' : '已确认收货并完成退款处理')
  await Promise.all([fetchOrders(), fetchWorkSummary()])
}

const openExchangeShipment = (sale) => {
  currentAfterSale.value = sale
  exchangeShipmentForm.value = { deliveryCompany: '', deliveryNo: '' }
  exchangeShipmentDialogVisible.value = true
}

const submitExchangeShipment = async () => {
  const deliveryCompany = exchangeShipmentForm.value.deliveryCompany?.trim()
  const deliveryNo = exchangeShipmentForm.value.deliveryNo?.trim()
  if (!deliveryCompany || !/^[A-Za-z0-9_-]{4,64}$/.test(deliveryNo || '')) {
    ElMessage.warning('请选择物流公司，并填写4至64位字母、数字、下划线或短横线组成的物流单号')
    return
  }
  exchangeShipmentLoading.value = true
  try {
    await shipShopAfterSaleExchangeReplacement(currentAfterSale.value.id, { deliveryCompany, deliveryNo })
    ElMessage.success('换货商品已发出，库存已扣减，等待客户确认收货')
    exchangeShipmentDialogVisible.value = false
    await Promise.all([fetchOrders(), fetchWorkSummary()])
  } finally {
    exchangeShipmentLoading.value = false
  }
}

onMounted(() => {
  fetchOrders()
  fetchWorkSummary()
  window.addEventListener('admin-order-work-summary', handleWorkSummaryUpdate)
  window.addEventListener('admin-order-changed', handleRealtimeOrderChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('admin-order-work-summary', handleWorkSummaryUpdate)
  window.removeEventListener('admin-order-changed', handleRealtimeOrderChange)
})
</script>

<style scoped>
.order-state-nav {
  display: flex;
  gap: 28px;
  margin-bottom: 18px;
  padding: 0 18px;
  overflow-x: auto;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  scrollbar-width: none;
}

.order-state-nav::-webkit-scrollbar {
  display: none;
}

.order-state-nav button {
  position: relative;
  flex: 0 0 auto;
  padding: 15px 3px 14px;
  color: #606266;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 15px;
}

.order-state-count {
  display: inline-flex;
  min-width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  margin-left: 5px;
  padding: 0 6px;
  color: #fff;
  background: #f56c6c;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
}

.order-state-nav button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  background: transparent;
  border-radius: 3px 3px 0 0;
  content: '';
}

.order-state-nav button:hover,
.order-state-nav button.active {
  color: #409eff;
  font-weight: 600;
}

.order-state-nav button.active::after {
  background: #409eff;
}

.order-search-panel {
  margin-bottom: 18px;
}

.order-search-panel :deep(.el-input) {
  width: 320px;
}

.order-item-cell-list {
  display: grid;
}

.service-remark-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.service-remark-preview {
  display: block;
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-remark-form {
  margin-top: 18px;
}

.customer-order-remark {
  width: 100%;
  min-height: 32px;
  padding: 5px 10px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 6px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.order-table :deep(.el-table__cell) {
  padding: 10px 6px;
}

.order-table :deep(.cell) {
  padding: 0 4px;
}

.order-item-cell {
  display: flex;
  min-height: 38px;
  align-items: center;
  padding: 7px 0;
}

.product-name-item strong {
  color: #303133;
  line-height: 1.45;
}

.product-spec-item {
  color: #909399;
  font-size: 13px;
  line-height: 1.45;
}

.product-quantity-item {
  justify-content: center;
  color: #303133;
  font-weight: 600;
}

.order-item-cell + .order-item-cell {
  border-top: 1px dashed #ebeef5;
}

.after-sale-summary + .refund-summary {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
}

.order-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.order-no {
  font-weight: 600;
  color: #303133;
}

.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.danger {
  color: #f56c6c;
  font-weight: 600;
}

.bonus-summary {
  margin-bottom: 16px;
}

.bonus-alert {
  margin-bottom: 16px;
}

.bonus-trace-metrics {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 18px;
}

.bonus-trace-metrics > div {
  min-width: 0;
  padding: 14px;
  background: #f7f9fc;
  border: 1px solid #e8edf4;
  border-radius: 10px;
}

.bonus-trace-metrics span {
  display: block;
  margin-bottom: 7px;
  color: #7a8494;
  font-size: 12px;
}

.bonus-trace-metrics strong {
  color: #303133;
  font-size: 18px;
}

.bonus-trace-metrics .primary-value {
  color: #409eff;
}

.bonus-trace-section {
  margin-top: 18px;
  padding: 18px;
  background: #fff;
  border: 1px solid #e8edf4;
  border-radius: 12px;
}

.bonus-trace-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.bonus-trace-title h3 {
  margin: 0;
  color: #303133;
  font-size: 16px;
}

.bonus-trace-title p,
.bonus-timeline p {
  margin: 5px 0 0;
  color: #7a8494;
  font-size: 12px;
  line-height: 1.6;
}

.bonus-timeline {
  padding: 8px 4px 0;
}

.bonus-program-summary {
  margin-bottom: 14px;
}

.bonus-evidence-table {
  margin-top: 14px;
}

.bonus-audit-collapse {
  margin-top: 18px;
  border: 1px solid #e8edf4;
  border-radius: 12px;
  overflow: hidden;
}

.bonus-audit-collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 64px;
  padding: 12px 18px;
  border-bottom: 0;
}

.bonus-audit-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.bonus-audit-collapse :deep(.el-collapse-item__content) {
  padding: 0 18px 18px;
}

.bonus-audit-collapse-title {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.bonus-audit-collapse-title span {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}

.bonus-audit-section {
  margin-top: 14px;
  background: #fafbfd;
}

@media (max-width: 1200px) {
  .bonus-trace-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.merchant-order-scope-tip {
  margin-bottom: 16px;
}

.trade-child-table {
  margin-top: 16px;
}

.order-batch-actions :deep(.el-form-item__content) {
  display: flex;
  gap: 8px;
}

.order-batch-actions .el-button + .el-button {
  margin-left: 0;
}

.shipping-workflow-tip {
  margin: -2px 0 16px;
}

.search-feedback {
  margin-bottom: 16px;
}

.shipment-result-summary {
  margin-top: 16px;
}

.shipment-error-table {
  margin-top: 16px;
}

.shipment-list > div + div {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
}

.existing-shipments {
  width: 100%;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  line-height: 1.8;
}
.auto-receive-deadline { margin-top: 4px; color: #b26a00; font-weight: 600; }
.after-sale-action-deadline { display: grid; gap: 2px; margin-top: 5px; color: #8a650f; font-size: 11px; line-height: 1.4; }
.after-sale-action-deadline.overdue { color: #d92d20; font-weight: 700; }
.after-sale-action-alert { margin-bottom: 16px; }

.remaining-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 13px;
}

.manual-refund-summary {
  margin: 16px 0;
}

.manual-refund-form {
  margin-top: 8px;
}

.manual-refund-items .remaining-tip {
  margin: 3px 0 0;
  color: #909399;
  font-size: 12px;
}

.manual-refund-amount {
  color: #e6a23c;
  font-size: 20px;
  font-weight: 700;
}

.after-sale-reason {
  width: 100%;
  color: #606266;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.after-sale-proof-grid {
  display: grid;
  grid-template-columns: repeat(4, 72px);
  gap: 8px;
}

.after-sale-proof-grid :deep(.el-image) {
  width: 72px;
  height: 72px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f5f7fa;
}
</style>
