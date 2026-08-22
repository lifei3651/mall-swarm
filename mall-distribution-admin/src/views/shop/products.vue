<template>
  <div class="page-container product-page">
    <div class="page-heading">
      <div>
        <h2>商品中心</h2>
        <p>集中维护商品内容、价格库存、配送与售后服务</p>
      </div>
      <el-tag type="info" effect="plain">上架前信息一站式校验</el-tag>
    </div>
    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词"><el-input v-model="query.keyword" placeholder="商品名称/商品编号" clearable @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryName" clearable filterable placeholder="全部分类" style="width: 160px" @change="handleSearch">
            <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.categoryName" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px" @change="handleSearch">
            <el-option label="上架" :value="1" /><el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="库存">
          <el-select v-model="query.stockStatus" clearable placeholder="全部库存" style="width: 130px" @change="handleSearch">
            <el-option label="库存正常" value="NORMAL" /><el-option label="低库存" value="LOW" /><el-option label="已缺货" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openDialog()">新增商品</el-button>
        </el-form-item>
      </el-form>
      <div v-if="!isMerchantUser" class="pv-global-setting">
        <div>
          <strong>商品 PV 填写</strong>
          <span>开启后，在每个商品发布页单独填写；关闭后所有商品按 PV=0 处理，不再参与 PV 计算。</span>
        </div>
        <el-switch v-model="performanceUnitsEnabled" inline-prompt active-text="开" inactive-text="关" :disabled="!store.hasPermission('config:bonus')" @change="changePvSetting" />
      </div>
    </div>

    <div v-if="selectedRows.length" class="batch-toolbar">
      <span>已选择 {{ selectedRows.length }} 个商品</span>
      <el-button v-if="!selectedRows.some((row) => row.merchantId)" size="small" type="success" :loading="batchLoading" @click="batchSetStatus(1)">批量上架</el-button>
      <el-button size="small" type="warning" :loading="batchLoading" @click="batchSetStatus(0)">批量下架</el-button>
      <el-button size="small" text @click="selectedRows = []">清空选择</el-button>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="tableData" v-loading="loading" :empty-text="tableEmptyText" stripe row-key="id" style="width: 100%" @selection-change="selectedRows = $event">
      <el-table-column type="selection" width="48" />
      <el-table-column label="商品信息" min-width="310">
        <template #default="{ row }">
          <div class="product-cell">
            <el-image class="cover" :src="row.coverUrl" fit="cover"><template #error><div class="cover-fallback">图</div></template></el-image>
            <div class="product-meta">
              <div class="name">{{ row.productName }}</div>
              <div class="sub product-number">商品编号：{{ row.productNo || '未设置' }}</div>
              <div class="sub">{{ row.subtitle || '未填写商品卖点' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="商品分类" width="120"><template #default="{ row }"><span :class="{ 'muted-value': !row.categoryName }">{{ row.categoryName || '未分类' }}</span></template></el-table-column>
      <el-table-column label="销售方" width="140"><template #default="{ row }">{{ row.merchantName || '平台自营' }}</template></el-table-column>
      <el-table-column prop="salePrice" label="展示售价" width="110"><template #default="{ row }">¥{{ row.salePrice }}</template></el-table-column>
      <el-table-column prop="costAmount" label="结算价" width="125"><template #default="{ row }"><strong :class="{ 'merchant-settlement': row.merchantId }">¥{{ row.costAmount }}</strong></template></el-table-column>
      <el-table-column label="结算等待" width="120"><template #default="{ row }"><span v-if="row.merchantId">{{ effectiveSettlementDays(row) }} 天</span><span v-else>-</span></template></el-table-column>
      <el-table-column v-if="performanceUnitsEnabled" prop="pvValue" label="单件PV" width="120">
        <template #default="{ row }">
          <span :class="{ 'pv-invalid': Number(row.pvValue || 0) > Number(row.salePrice || 0) }">{{ Number(row.pvValue || 0) }}</span>
          <el-tooltip v-if="Number(row.pvValue || 0) > Number(row.salePrice || 0)" content="PV超过销售价，请编辑商品并修正" placement="top"><el-icon class="pv-warning"><WarningFilled /></el-icon></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="库存状态" width="128">
        <template #default="{ row }">
          <div class="stock-cell"><strong>{{ row.stock ?? 0 }}</strong><el-tag size="small" :type="stockState(row).type">{{ stockState(row).label }}</el-tag></div>
          <div class="stock-help">安全库存 {{ row.safetyStock ?? 0 }}</div>
        </template>
      </el-table-column>
      <el-table-column label="会员限购" width="105"><template #default="{ row }">{{ Number(row.purchaseLimit || 0) > 0 ? `每人 ${row.purchaseLimit} 件` : '不限购' }}</template></el-table-column><el-table-column prop="salesCount" label="累计销量" width="95" />
      <el-table-column prop="sort" label="上架排序" width="95" />
      <el-table-column prop="status" label="上架状态" width="95"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag></template></el-table-column>
      <el-table-column label="审核状态" width="105"><template #default="{ row }"><el-tag v-if="row.merchantId" :type="reviewState(row).type">{{ reviewState(row).label }}</el-tag><span v-else>-</span></template></el-table-column>
      <el-table-column label="操作" fixed="right" width="210">
        <template #default="{ row }">
          <el-button v-if="canManageProducts" type="primary" link :disabled="row.merchantReviewStatus === 'PENDING'" @click="editProduct(row)">编辑商品</el-button>
          <el-button v-if="canManageProducts" :type="row.status === 1 ? 'warning' : 'success'" link :disabled="row.merchantReviewStatus === 'PENDING'" @click="toggleStatus(row)">{{ productActionLabel(row) }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pagination-container" background layout="total, prev, pager, next, sizes" :total="pagination.total" v-model:current-page="pagination.page" v-model:page-size="pagination.size" @current-change="fetchData" @size-change="fetchData" />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品' : '发布新商品'" fullscreen destroy-on-close class="publish-dialog" :before-close="confirmCloseProductDialog">
      <div class="publish-shell" v-loading="dialogLoading">
        <el-alert title="按实际情况填写即可：商品名称和卖点尽量简洁，分类可不设置；带规格的商品请在“规格、价格与库存”中维护每个 SKU，第一张主图将作为商品封面。" type="info" :closable="false" />
        <div class="publish-layout">
          <nav class="publish-nav" aria-label="商品编辑步骤">
            <button v-for="item in sectionAnchors" :key="item.id" type="button" @click="scrollToSection(item.id)">{{ item.label }}</button>
          </nav>
          <el-form :model="form" label-width="108px" class="publish-form">
          <section id="product-basic" class="form-section">
            <h3>1. 基本信息</h3>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="商品名称" required><el-input v-model="form.productName" maxlength="60" show-word-limit placeholder="建议使用简短、易识别的商品名称" /><div class="field-help">最多 60 个字，方便顾客快速识别。</div></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="商品编号"><el-input v-model="form.productNo" placeholder="留空自动生成" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="商品卖点"><el-input v-model="form.subtitle" maxlength="80" show-word-limit placeholder="用一句话说明最值得购买的理由" /><div class="field-help">最多 80 个字，突出 1～2 个核心卖点即可。</div></el-form-item>
            <el-form-item label="销售方"><el-select v-model="form.merchantId" clearable filterable placeholder="平台自营" style="width:360px" :disabled="!canChangeMerchant" @change="changeMerchant"><el-option v-for="item in merchants" :key="item.id" :label="item.merchantName" :value="item.id" /></el-select><div class="field-help">商户账号固定为本商户；平台账号留空表示自营。历史订单使用下单快照，新订单使用审核后的最新结算价。</div></el-form-item>
            <el-alert v-if="form.merchantId" title="商户商品保存后保持下架，提交审核并通过后自动上架。修改销售价或结算价前必须先下架，修改后重新审核。" type="warning" :closable="false" show-icon style="margin-bottom:18px" />
            <el-form-item v-if="form.merchantId && !isMerchantUser" label="结算价变更原因"><el-input v-model="form.settlementCostChangeReason" maxlength="200" show-word-limit placeholder="例如：依据新供货合同调整" /><div class="field-help">平台人员修改商户或结算价时必填；商户提交的价格由审核记录留痕。</div></el-form-item>
            <el-form-item v-if="form.merchantId" label="结算等待">
              <el-select v-model="form.settlementDelayMode" style="width:220px" :disabled="isMerchantUser" @change="changeSettlementDelayMode">
                <el-option :label="`跟随商户默认（${selectedMerchantDefaultDays}天）`" value="DEFAULT" />
                <el-option label="该商品单独设置" value="OVERRIDE" />
              </el-select>
              <el-input-number v-if="form.settlementDelayMode === 'OVERRIDE'" v-model="form.settlementDelayDaysOverride" :min="0" :max="365" :precision="0" :disabled="isMerchantUser" style="width:150px;margin-left:12px" />
              <span v-if="form.settlementDelayMode === 'OVERRIDE'" style="margin-left:8px">天</span>
              <div class="field-help">从客户确认收货与售后入口截止时间中的较晚时点开始计算；风险商品可单独设置30天。历史订单使用下单时快照。</div>
            </el-form-item>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="商品分类">
                  <div class="category-picker">
                    <el-select v-model="form.categoryName" clearable filterable placeholder="不设置分类也可以"><el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.categoryName" /></el-select>
                    <el-button v-if="!isMerchantUser" type="primary" plain :icon="Plus" @click="openQuickCategory">新增分类</el-button>
                  </div>
                  <div class="field-help">商品较少时可以不分类；需要筛选和分组时再选择即可。</div>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="排序"><el-input-number v-model="form.sort" :step="1" style="width:100%" /><div class="field-help">上架商品自动排在下架商品前；同状态下数值越大越靠前。</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="上架状态"><span v-if="form.merchantId">保存为下架草稿，审核通过后自动上架</span><el-radio-group v-else v-model="form.status"><el-radio-button :value="1">立即上架</el-radio-button><el-radio-button :value="0">暂存下架</el-radio-button></el-radio-group></el-form-item></el-col>
            </el-row>
          </section>

          <section id="product-images" class="form-section">
            <h3>2. 商品主图</h3>
            <el-form-item label="主图（最多5张）" required>
              <div class="image-manager">
                <div v-for="(url, index) in form.mainImages" :key="url + index" class="image-tile">
                  <el-image :src="url" fit="cover" />
                  <span v-if="index === 0" class="cover-badge">封面</span>
                  <div class="image-actions"><button v-if="index > 0" type="button" @click="setCover(index)">设为封面</button><button type="button" @click="removeImage('mainImages', index)">删除</button></div>
                </div>
                <el-upload v-if="form.mainImages.length < 5" action="#" multiple :show-file-list="false" accept="image/*" :http-request="({ file }) => uploadImageTo('mainImages', file, 5)"><div class="image-uploader"><el-icon><Plus /></el-icon><span>上传主图</span></div></el-upload>
              </div>
              <div class="field-help">建议 800×800 像素以上，支持 JPG/PNG/WEBP/GIF，单张不超过 5MB；可上传 1～5 张。</div>
            </el-form-item>
          </section>

          <section id="product-stock" class="form-section">
            <div class="section-title product-type-title">
              <h3>3. 价格、库存与规格</h3>
              <el-radio-group :model-value="hasSku ? 'MULTI' : 'SINGLE'" @change="changeProductType">
                <el-radio-button value="SINGLE">单规格商品</el-radio-button>
                <el-radio-button value="MULTI">多规格商品</el-radio-button>
              </el-radio-group>
            </div>

            <template v-if="!hasSku">
              <el-alert title="单规格商品只需在这里填写一组价格和库存，商城下单直接使用这组数据。" type="success" :closable="false" show-icon style="margin-bottom:18px" />
              <el-row :gutter="20">
                <el-col :span="6"><el-form-item label="销售价" required><el-input-number v-model="form.salePrice" :min="0" :precision="2" :step="1" controls-position="right" class="money-input" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="划线价"><el-input-number v-model="form.marketPrice" :min="0" :precision="2" :step="1" controls-position="right" class="money-input" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item :label="form.merchantId ? '结算价' : '参考成本价'"><el-input-number v-model="form.costAmount" :min="0" :precision="2" :step="1" controls-position="right" class="money-input" :disabled="Boolean(form.merchantId) && !canManageSettlementCost" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="可售库存"><el-input-number v-model="form.stock" :min="0" :step="1" controls-position="right" class="money-input" /></el-form-item></el-col>
              </el-row>
              <el-row v-if="performanceUnitsEnabled" :gutter="20" class="pv-row">
                <el-col :span="10"><el-form-item label="单件PV"><el-input-number v-model="form.pvValue" :min="0" :max="productPvLimit" :precision="2" controls-position="right" class="money-input" /><div class="field-help">购买数量会自动相乘；填0表示没有PV。单件PV不能超过销售价，奖金仍按实付商品金额计算。</div></el-form-item></el-col>
              </el-row>
            </template>

            <template v-else>
              <el-alert :title="form.merchantId ? '多规格商品只维护下面的SKU。顾客下单时，销售价、结算价、PV和库存均以选中的SKU为准；商品列表所需的展示价和总库存由系统自动汇总。' : '多规格商品只维护下面的SKU。顾客下单时，销售价、参考成本、PV和库存均以选中的SKU为准；商品列表所需的展示价和总库存由系统自动汇总。'" type="success" :closable="false" show-icon style="margin-bottom:14px" />
              <div class="sku-toolbar">
                <span>每一行代表一种可购买规格，例如“体验装”“家庭装”；可在规格属性中填写“包装规格：家庭装”。</span>
                <el-button type="primary" plain :icon="Plus" @click="addSku">添加规格</el-button>
              </div>
              <el-row v-if="performanceUnitsEnabled" :gutter="20" class="pv-row multi-pv-row">
                <el-col :span="10"><el-form-item label="统一默认PV"><el-input-number v-model="form.pvValue" :min="0" :max="productPvLimit" :precision="2" controls-position="right" class="money-input" /><div class="field-help">可选。某个SKU的PV填0时继承这里的数值；如各规格PV不同，请直接在对应SKU行填写。</div></el-form-item></el-col>
              </el-row>
              <div class="sku-table-wrap">
              <el-table :data="skuRows" border>
                <el-table-column label="规格图" width="92" align="center">
                  <template #default="{ row }"><el-upload action="#" :show-file-list="false" accept="image/*" :http-request="({ file }) => uploadSkuImage(row, file)"><el-image v-if="row.imageUrl" :src="row.imageUrl" class="sku-image" fit="cover" /><div v-else class="sku-image-placeholder"><el-icon><Plus /></el-icon></div></el-upload></template>
                </el-table-column>
                <el-table-column label="规格名称" min-width="160"><template #default="{ row }"><el-input v-model="row.skuName" placeholder="例如：家庭装" /></template></el-table-column>
                <el-table-column label="规格属性" min-width="220">
                  <template #default="{ row, $index }">
                    <div class="sku-attributes">
                      <el-tag v-for="item in row.attributes" :key="`${item.name}:${item.value}`" size="small">{{ item.name }}：{{ item.value }}</el-tag>
                      <span v-if="!row.attributes?.length" class="empty-attribute">未设置</span>
                      <el-button type="primary" link @click="openSkuAttributes(row, $index)">编辑属性</el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="SKU编码" min-width="150"><template #default="{ row }"><el-input v-model="row.skuNo" placeholder="留空自动生成" /></template></el-table-column>
                <el-table-column label="销售价" width="150"><template #default="{ row }"><el-input-number v-model="row.salePrice" :min="0" :precision="2" controls-position="right" /></template></el-table-column>
                <el-table-column label="划线价" width="150"><template #default="{ row }"><el-input-number v-model="row.marketPrice" :min="0" :precision="2" controls-position="right" /></template></el-table-column>
                <el-table-column :label="form.merchantId ? '结算价' : '参考成本价'" width="150"><template #default="{ row }"><el-input-number v-model="row.costAmount" :min="0" :precision="2" controls-position="right" :disabled="Boolean(form.merchantId) && !canManageSettlementCost" /></template></el-table-column>
                <el-table-column v-if="performanceUnitsEnabled" label="单件PV" width="165">
                  <template #header><span>单件PV</span><el-tooltip content="填0继承上方默认单件PV；可为不同规格单独设置，但不能超过该SKU销售价" placement="top"><el-icon class="column-help"><QuestionFilled /></el-icon></el-tooltip></template>
                  <template #default="{ row }"><el-input-number v-model="row.pvValue" :min="0" :max="Math.max(0, Number(row.salePrice || 0))" :precision="2" controls-position="right" /></template>
                </el-table-column>
                <el-table-column label="库存" width="140"><template #default="{ row }"><el-input-number v-model="row.stock" :min="0" controls-position="right" /></template></el-table-column>
                <el-table-column label="安全库存" width="140"><template #default="{ row }"><el-input-number v-model="row.safetyStock" :min="0" controls-position="right" /></template></el-table-column>
                <el-table-column label="启用" width="76" align="center"><template #default="{ row }"><el-switch v-model="row.status" :active-value="1" :inactive-value="0" /></template></el-table-column>
                <el-table-column label="操作" width="76" fixed="right"><template #default="{ $index }"><el-button type="danger" link @click="removeSku($index)">删除</el-button></template></el-table-column>
              </el-table>
            </div>
            </template>
            <el-row :gutter="20">
              <el-col :span="8"><el-form-item label="每位会员限购"><el-input-number v-model="form.purchaseLimit" :min="0" :step="1" controls-position="right" class="money-input" /><div class="field-help">按会员累计购买数量计算；0 表示不限购。</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="商品安全库存"><el-input-number v-model="form.safetyStock" :min="0" :step="1" controls-position="right" class="money-input" /><div class="field-help">单规格直接预警；多规格作为商品汇总阈值。</div></el-form-item></el-col>
            </el-row>
            <el-alert class="cost-help" :title="form.merchantId ? '结算价是平台应付给商户的单件货款；历史订单使用下单时快照，不会被后续改价影响。' : '平台自营参考成本价只用于经营利润统计；有规格商品按所选SKU的成本计算。'" type="warning" :closable="false" show-icon />
          </section>

          <section id="product-business" class="form-section">
            <h3>4. 销售渠道</h3>
            <el-alert title="普通商城、复购区、报单区是三种业务渠道。报单区和客户定制奖金先完成配置，未完成前不会开放下单。" type="info" :closable="false" show-icon style="margin-bottom:18px" />
            <el-row :gutter="20">
              <el-col :span="8"><el-form-item label="普通商城"><el-switch v-model="form.normalSaleEnabled" :active-value="1" :inactive-value="0" active-text="销售" inactive-text="不销售" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="复购商城"><el-switch v-model="form.repurchaseSaleEnabled" :active-value="1" :inactive-value="0" active-text="销售" inactive-text="不销售" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="报单区"><el-switch v-model="form.enrollmentSaleEnabled" :active-value="1" :inactive-value="0" active-text="预配置" inactive-text="不进入" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="团队奖金"><el-select v-model="form.teamBonusMode" style="width:360px"><el-option v-if="!form.merchantId" label="继承商城现有规则" value="INHERIT"/><el-option label="不产生团队奖金" value="NONE"/><el-option label="使用现有标准奖金" value="STANDARD"/><el-option label="客户定制（配置后开放）" value="CUSTOM"/></el-select><div class="field-help">商户商品必须明确选择；产生团队奖金的商品只放复购区或报单区，不进入公开普通商城。</div></el-form-item>
            <el-row v-if="form.repurchaseSaleEnabled === 1" :gutter="20">
              <el-col :span="8"><el-form-item label="商品复购价" required><el-input-number v-model="form.repurchasePrice" :min="0.01" :precision="2" controls-position="right" style="width:100%" /><div class="field-help">多规格可在SKU单独覆盖，未填写时继承此价格。</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="商品复购PV"><el-input-number v-model="form.repurchasePv" :min="0" :max="Number(form.repurchasePrice || 0)" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="复购累计限购"><el-input-number v-model="form.repurchasePurchaseLimit" :min="0" :step="1" controls-position="right" style="width:100%" /><div class="field-help">0表示不限购，仅统计复购订单。</div></el-form-item></el-col>
            </el-row>
            <el-table v-if="form.repurchaseSaleEnabled === 1 && hasSku" :data="skuRows" border size="small">
              <el-table-column prop="skuName" label="规格" min-width="150" />
              <el-table-column label="SKU复购价（可选）" min-width="190"><template #default="{ row }"><el-input-number v-model="row.repurchasePrice" :min="0" :precision="2" controls-position="right" /></template></el-table-column>
              <el-table-column label="SKU复购PV（可选）" min-width="190"><template #default="{ row }"><el-input-number v-model="row.repurchasePv" :min="0" :max="Number(row.repurchasePrice || form.repurchasePrice || 0)" :precision="2" controls-position="right" /></template></el-table-column>
            </el-table>
          </section>

          <section id="product-delivery" class="form-section">
            <h3>5. 物流配送</h3>
            <el-row :gutter="20">
              <el-col v-if="!form.shippingAddressId" :span="8"><el-form-item label="发货地区" required><el-cascader v-model="deliveryRegion" :options="pcaTextArr" placeholder="请选择省 / 市 / 区县" style="width:100%" filterable /><div class="field-help">未选择地址簿时，可直接填写商品发货地区</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="发货时效"><el-select v-model="form.deliveryTime" placeholder="请选择" style="width:100%"><el-option label="24小时内发货" value="24小时内发货" /><el-option label="48小时内发货" value="48小时内发货" /><el-option label="72小时内发货" value="72小时内发货" /><el-option label="预售，按约定时间发货" value="预售" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="配送方式"><el-select v-model="form.freightType" style="width:100%"><el-option label="全国包邮" :value="0" /><el-option label="统一运费" :value="1" /><el-option label="满额包邮" :value="2" /><el-option label="按地区配送" :value="3" /></el-select></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20" class="service-address-row">
              <el-col :span="8"><el-form-item label="商城发货地址"><el-select v-model="form.shippingAddressId" clearable filterable placeholder="选择仓库发货地址" style="width:100%" @change="applyShippingAddress"><el-option v-for="item in shippingAddresses" :key="item.id" :label="addressOptionLabel(item)" :value="item.id" /></el-select><div class="field-help"><router-link to="/shop/service-addresses">管理地址簿</router-link></div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="售后退货地址"><el-select v-model="form.returnAddressId" clearable filterable placeholder="默认退货地址" style="width:100%"><el-option v-for="item in returnAddresses" :key="item.id" :label="addressOptionLabel(item)" :value="item.id" /></el-select><div class="field-help">审核通过后展示给客户</div></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col v-if="form.freightType === 1" :span="8"><el-form-item label="统一运费"><el-input-number v-model="form.freightAmount" :min="0" :precision="2" class="money-input" /></el-form-item></el-col>
              <el-col v-if="form.freightType === 2" :span="8"><el-form-item label="未满额运费"><el-input-number v-model="form.freightAmount" :min="0" :precision="2" class="money-input" /></el-form-item></el-col>
              <el-col v-if="form.freightType === 2" :span="8"><el-form-item label="满多少包邮"><el-input-number v-model="form.freeShippingAmount" :min="0" :precision="2" class="money-input" /></el-form-item></el-col>
              <el-col v-if="form.freightType === 3" :span="16">
                <el-form-item label="配送规则" required>
                  <div class="template-picker">
                    <el-select v-model="form.freightTemplateId" placeholder="请选择模板" style="min-width:280px"><el-option v-for="item in activeFreightTemplates" :key="item.id" :label="item.templateName" :value="item.id" /></el-select>
                    <el-button v-if="!isMerchantUser" type="primary" plain @click="openFreightTemplate()">新建模板</el-button>
                    <el-button v-if="!isMerchantUser" :disabled="!form.freightTemplateId" @click="editSelectedFreightTemplate">编辑当前模板</el-button>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-alert title="运费会加入订单实付和财务金额，但不计入业绩、累计单量金额或任何奖金计算。" type="success" :closable="false" show-icon />
          </section>

          <section id="product-after-sale" class="form-section">
            <h3>6. 售后及服务</h3>
            <el-form-item label="服务保障">
              <div class="guarantee-tags">
                <div class="guarantee-tags-list">
                  <el-tooltip v-for="(item, index) in form.serviceGuarantees" :key="index" :content="item.description || '暂无说明'" placement="top" :show-after="300">
                    <el-check-tag :checked="item.enabled" @change="item.enabled = !item.enabled" class="guarantee-tag">
                      <el-icon><component :is="getGuaranteeIcon(item.icon)" /></el-icon>
                      <span>{{ item.title }}</span>
                    </el-check-tag>
                  </el-tooltip>
                  <el-button type="primary" link :icon="Plus" @click="addCustomGuarantee" class="add-custom-btn">自定义保障</el-button>
                </div>
                <div class="guarantee-tags-help">点击标签启用/禁用，悬停查看详情</div>
              </div>
            </el-form-item>
            <el-form-item label="售后说明">
              <div class="after-sale-compact">
                <div class="after-sale-summary" @click="afterSaleExpanded = !afterSaleExpanded">
                  <div class="after-sale-summary-left">
                    <el-icon><Document /></el-icon>
                    <span class="after-sale-summary-label">{{ afterSaleSummaryLabel }}</span>
                    <span class="after-sale-summary-preview">{{ afterSalePreviewText }}</span>
                  </div>
                  <el-icon class="after-sale-expand-icon" :class="{ expanded: afterSaleExpanded }"><ArrowDown /></el-icon>
                </div>
                <el-collapse-transition>
                  <div v-show="afterSaleExpanded" class="after-sale-detail">
                    <div class="after-sale-detail-toolbar">
                      <el-select v-model="form.afterSalePresetKey" placeholder="选择售后模板" @change="applyAfterSalePreset" style="width: 240px;">
                        <el-option v-for="option in afterSalePresetOptions" :key="option.value" :label="option.label" :value="option.value" />
                        <el-option label="自定义售后说明" value="custom" />
                      </el-select>
                      <span class="after-sale-detail-hint">选择模板自动填充，也可手动修改</span>
                    </div>
                    <el-input v-model="form.afterSalePolicy" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="售后说明内容" />
                    <div class="field-help">请确认文案与实际售后能力一致；页面底部会按换行展示这些内容。</div>
                  </div>
                </el-collapse-transition>
              </div>
            </el-form-item>
          </section>

          <section id="product-detail" class="form-section">
            <h3>6. 商品详情</h3>
            <el-form-item label="文字详情"><el-input v-model="form.detail" type="textarea" :rows="6" placeholder="商品参数、使用说明、注意事项等" /></el-form-item>
            <el-form-item label="详情图（最多30张）">
              <div class="image-manager detail-manager">
                <div v-for="(url, index) in form.detailImageUrls" :key="url + index" class="image-tile detail-tile"><el-image :src="url" fit="cover" /><span class="image-order">{{ index + 1 }}</span><div class="image-actions"><button type="button" @click="removeImage('detailImageUrls', index)">删除</button></div></div>
                <el-upload v-if="form.detailImageUrls.length < 30" action="#" multiple :show-file-list="false" accept="image/*" :http-request="({ file }) => uploadImageTo('detailImageUrls', file, 30)"><div class="image-uploader"><el-icon><Plus /></el-icon><span>上传详情图</span></div></el-upload>
              </div>
              <div class="field-help">按显示顺序上传，可连续添加多张，最多 30 张；每张不超过 5MB。</div>
            </el-form-item>
          </section>
          </el-form>
        </div>
      </div>
      <template #footer><div class="dialog-footer"><el-button size="large" @click="confirmCloseProductDialog(() => { dialogVisible = false })">取消</el-button><el-button type="primary" size="large" :loading="submitting" @click="submitForm">保存商品</el-button></div></template>
    </el-dialog>

    <el-dialog v-model="customGuaranteeVisible" title="编辑服务保障" width="520px" append-to-body destroy-on-close>
      <el-form :model="customGuaranteeForm" label-width="80px">
        <el-form-item label="保障图标">
          <el-select v-model="customGuaranteeForm.icon" placeholder="选择图标" style="width: 100%;">
            <el-option v-for="option in guaranteeIconOptions" :key="option.value" :label="option.label" :value="option.value">
              <span style="display: flex; align-items: center; gap: 8px;"><el-icon><component :is="getGuaranteeIcon(option.value)" /></el-icon><span>{{ option.label }}</span></span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="保障名称" required><el-input v-model="customGuaranteeForm.title" maxlength="20" show-word-limit placeholder="如：正品保障、七天无理由" /></el-form-item>
        <el-form-item label="保障说明" required><el-input v-model="customGuaranteeForm.description" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="简要说明该保障的具体内容" /></el-form-item>
        <el-form-item label="前台展示"><el-switch v-model="customGuaranteeForm.enabled" active-text="显示" inactive-text="隐藏" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="customGuaranteeVisible = false">取消</el-button>
        <el-button v-if="customGuaranteeEditIndex >= 0" type="danger" plain @click="removeCustomGuarantee">删除</el-button>
        <el-button type="primary" @click="saveCustomGuarantee">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="freightTemplateDialogVisible" :title="freightTemplateForm.id ? '编辑运费模板' : '新建运费模板'" width="980px" append-to-body destroy-on-close>
      <el-form :model="freightTemplateForm" label-width="118px">
        <el-form-item label="模板名称" required><el-input v-model="freightTemplateForm.templateName" maxlength="128" /></el-form-item>
        <el-row :gutter="20">
          <el-col :span="10"><el-form-item label="其余地区"><el-select v-model="freightTemplateForm.defaultMode" style="width:100%"><el-option label="包邮配送" value="FREE" /><el-option label="收取统一运费" value="FIXED" /><el-option label="不配送" value="UNAVAILABLE" /></el-select></el-form-item></el-col>
          <el-col v-if="freightTemplateForm.defaultMode === 'FIXED'" :span="8"><el-form-item label="统一运费"><el-input-number v-model="freightTemplateForm.defaultFreightAmount" :min="0" :precision="2" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="模板状态"><el-switch v-model="freightTemplateForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-col>
        </el-row>
        <div class="section-title"><h3>指定地区配送规则</h3><el-button type="primary" plain :icon="Plus" @click="addFreightRule">添加地区规则</el-button></div>
        <el-alert title="勾选省份会自动勾选该省全部市、区县；也可以展开后只选择部分地区。下方会完整显示全部已选地区。" type="info" :closable="false" style="margin-bottom:14px" />
        <el-table :data="freightTemplateForm.rules" border>
          <el-table-column label="地区" min-width="460"><template #default="{ row }"><el-cascader v-model="row.regionPaths" class="freight-region-cascader" :options="pcaTextArr" :props="freightRegionProps" clearable filterable :collapse-tags="false" :show-all-levels="true" style="width:100%" placeholder="选择省/市/区县" /></template></el-table-column>
          <el-table-column label="配送规则" width="160"><template #default="{ row }"><el-select v-model="row.mode"><el-option label="包邮配送" value="FREE" /><el-option label="加收运费" value="FIXED" /><el-option label="不配送" value="UNAVAILABLE" /></el-select></template></el-table-column>
          <el-table-column label="运费" width="150"><template #default="{ row }"><el-input-number v-if="row.mode === 'FIXED'" v-model="row.freightAmount" :min="0" :precision="2" style="width:120px" /><span v-else>-</span></template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button type="danger" link @click="freightTemplateForm.rules.splice($index, 1)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="freightTemplateDialogVisible = false">取消</el-button><el-button type="primary" :loading="freightTemplateSaving" @click="saveFreightTemplateForm">保存模板</el-button></template>
    </el-dialog>

    <el-dialog v-model="quickCategoryVisible" title="新增商品分类" width="520px" append-to-body destroy-on-close>
      <el-form :model="quickCategoryForm" label-width="86px">
        <el-form-item label="分类名称" required><el-input v-model="quickCategoryForm.categoryName" maxlength="64" show-word-limit placeholder="例如：护理套装" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="quickCategoryForm.sort" :min="0" :max="999999" /><span class="quick-help">数值越大越靠前</span></el-form-item>
        <el-form-item label="备注"><el-input v-model="quickCategoryForm.remark" type="textarea" :rows="3" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <el-alert title="保存后会自动选中这个新分类；分类图标可在“商城管理 → 分类管理（可新增）”中补充。" type="info" :closable="false" show-icon />
      <template #footer><el-button @click="quickCategoryVisible = false">取消</el-button><el-button type="primary" :loading="quickCategorySaving" @click="saveQuickCategory">保存并选中</el-button></template>
    </el-dialog>

    <el-dialog v-model="skuAttributeVisible" title="编辑规格属性" width="560px" append-to-body>
      <el-alert title="例如：属性名填写“颜色”，属性值填写“红色”。可以添加颜色、尺寸、容量等多个属性。" type="info" :closable="false" style="margin-bottom:14px" />
      <div v-for="(item, index) in skuAttributeForm" :key="index" class="attribute-row">
        <el-input v-model="item.name" maxlength="30" placeholder="属性名，如颜色" />
        <el-input v-model="item.value" maxlength="60" placeholder="属性值，如红色" />
        <el-button type="danger" link @click="skuAttributeForm.splice(index, 1)">删除</el-button>
      </div>
      <el-button type="primary" plain :icon="Plus" @click="skuAttributeForm.push({ name: '', value: '' })">添加属性</el-button>
      <template #footer><el-button @click="skuAttributeVisible = false">取消</el-button><el-button type="primary" @click="saveSkuAttributes">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Box, CircleClose, Document, Medal, Money, Plus, QuestionFilled, Refresh, RefreshLeft, Search, Star, Van, WarningFilled } from '@element-plus/icons-vue'
import { pcaTextArr } from 'element-china-area-data'
import { createFreightTemplate, createShopCategory, getProductSettings, listFreightTemplates, listShopCategories, listShopProducts, listShopServiceAddresses, listShopSkus, publishShopProduct, submitMerchantProductReview, updateFreightTemplate, updateProductPvSetting, updateShopProductStatus, uploadShopImage } from '@/api/shop'
import { listMerchants } from '@/api/merchant'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { useAppStore } from '@/store'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const store = useAppStore()
const loading = ref(false)
const dialogLoading = ref(false)
const submitting = ref(false)
const batchLoading = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const dialogVisible = ref(false)
const stockStatusFromUrl = new URLSearchParams(window.location.search).get('stockStatus')
const query = ref({ keyword: '', categoryName: '', status: store.userInfo?.merchantId ? null : 1, stockStatus: ['NORMAL', 'LOW', 'OUT'].includes(stockStatusFromUrl) ? stockStatusFromUrl : null })
const pagination = ref({ page: 1, size: 10, total: 0 })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无商品')
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => {
    pagination.value.page = 1
    fetchData()
  },
)
const form = ref({})
const skuRows = ref([])
const removedSkuIds = ref([])
const categories = ref([])
const merchants = ref([])
const shippingAddresses = ref([])
const returnAddresses = ref([])
const freightTemplates = ref([])
const deliveryRegion = ref([])
const productSavedSnapshot = ref('')
const productEditSnapshot = computed(() => JSON.stringify({
  form: form.value, skus: skuRows.value, removedSkuIds: removedSkuIds.value, deliveryRegion: deliveryRegion.value,
}))
const hasUnsavedProduct = computed(() => dialogVisible.value
  && Boolean(productSavedSnapshot.value) && productEditSnapshot.value !== productSavedSnapshot.value)
useUnsavedChanges(hasUnsavedProduct, '商品资料、规格或配送设置尚未保存，确定离开吗？')
const freightTemplateDialogVisible = ref(false)
const freightTemplateSaving = ref(false)
const skuAttributeVisible = ref(false)
const skuAttributeForm = ref([])
const editingSkuIndex = ref(-1)
const quickCategoryVisible = ref(false)
const quickCategorySaving = ref(false)
const quickCategoryForm = ref({ categoryName: '', sort: 0, status: 1, remark: '' })
const freightRegionProps = { multiple: true, checkStrictly: false, emitPath: true }
const defaultFreightTemplateForm = () => ({ id: null, tenantId: 1, templateName: '', defaultMode: 'FREE', defaultFreightAmount: 0, status: 1, rules: [] })
const freightTemplateForm = ref(defaultFreightTemplateForm())
const customGuaranteeVisible = ref(false)
const customGuaranteeForm = ref({ enabled: true, presetKey: 'custom', icon: 'shield', title: '', description: '' })
const customGuaranteeEditIndex = ref(-1)
const afterSaleExpanded = ref(false)
const sectionAnchors = [
  { id: 'product-basic', label: '基本信息' },
  { id: 'product-images', label: '商品主图' },
  { id: 'product-stock', label: '价格与库存' },
  { id: 'product-business', label: '销售渠道' },
  { id: 'product-delivery', label: '物流配送' },
  { id: 'product-after-sale', label: '售后服务' },
  { id: 'product-detail', label: '商品详情' },
]
const activeFreightTemplates = computed(() => freightTemplates.value.filter((item) => item.status === 1))
const hasSku = computed(() => skuRows.value.length > 0)
const isMerchantUser = computed(() => Boolean(store.userInfo?.merchantId))
const canManageProducts = computed(() => store.hasPermission('shop:product'))
const canManageSettlementCost = computed(() => isMerchantUser.value || store.hasPermission('finance:manage'))
const canChangeMerchant = computed(() => !isMerchantUser.value && store.hasPermission('finance:manage'))
const selectedMerchantDefaultDays = computed(() => Number(merchants.value.find((item) => Number(item.id) === Number(form.value.merchantId))?.defaultSettlementDays || 0))
const effectiveSettlementDays = (row) => row?.settlementDelayDaysOverride == null
  ? Number(merchants.value.find((item) => Number(item.id) === Number(row?.merchantId))?.defaultSettlementDays || 0)
  : Number(row.settlementDelayDaysOverride)
const reviewState = (row) => ({ DRAFT: { label: '待提交', type: 'info' }, PENDING: { label: '审核中', type: 'warning' }, APPROVED: { label: '已通过', type: 'success' }, REJECTED: { label: '已驳回', type: 'danger' } }[row?.merchantReviewStatus] || { label: '待提交', type: 'info' })
const productActionLabel = (row) => {
  if (row.status === 1) return '下架'
  if (!row.merchantId || row.merchantReviewStatus === 'APPROVED') return '上架'
  return row.merchantReviewStatus === 'PENDING' ? '审核中' : '提交审核'
}
const productPvLimit = computed(() => {
  if (!hasSku.value) return Math.max(0, Number(form.value.salePrice || 0))
  const enabledPrices = skuRows.value
    .filter((item) => Number(item.status) === 1)
    .map((item) => Number(item.salePrice || 0))
    .filter(Number.isFinite)
  return enabledPrices.length ? Math.max(0, Math.min(...enabledPrices)) : 0
})
const performanceUnitsEnabled = ref(true)
const guaranteeIconOptions = [
  { value: 'shield', label: '盾牌保障' },
  { value: 'return', label: '退换货' },
  { value: 'package', label: '包裹售后' },
  { value: 'refund', label: '极速退款' },
  { value: 'ban', label: '限制说明' },
  { value: 'truck', label: '物流配送' },
  { value: 'heart', label: '贴心服务' },
  { value: 'badge', label: '品质认证' },
]
const guaranteeDefaults = {
  '七天无理由': { icon: 'return', description: '符合商城规则且商品完好的，可在商城当前配置的售后期限内申请无理由退货。' },
  '正品保障': { icon: 'shield', description: '商品来源与质量信息可追溯，具体以商品说明和售后规则为准。' },
  '极速退款': { icon: 'refund', description: '售后审核通过后，平台将尽快完成退款处理。' },
  '破损包赔': { icon: 'package', description: '商品运输途中发生破损，可凭有效凭证申请售后处理。' },
  '运费险': { icon: 'truck', description: '符合条件的退货订单可按保险规则获得退货运费补偿。' },
  '发货时效': { icon: 'truck', description: '发货时间以商品页面承诺为准，订单状态可全程查询。' },
  '退货运费': { icon: 'return', description: '符合商城售后规则的退货订单，运费按规则审核处理。' },
  '隐私发货': { icon: 'shield', description: '订单信息仅用于履约，包装展示以实际发货安排为准。' },
  '物流跟踪': { icon: 'package', description: '发货后提供物流单号，配送进度可在线查询。' },
}
const guaranteePresetOptions = Object.entries(guaranteeDefaults).map(([value]) => ({ value, label: value }))
const afterSalePolicyPresets = {
  basic: {
    label: '商城通用售后说明',
    content: '1. 签收商品时请先检查外包装和商品状态，如有破损、错发或漏发，请及时联系客服。\n2. 商品售后申请须符合商城交易与售后规则，并提供必要的订单信息和凭证。\n3. 退款金额以订单实际支付金额和审核结果为准，处理进度可在订单详情中查看。\n4. 退货运费承担方式以售后审核结果和商品页面说明为准。\n5. 不同商品可能存在特殊保存、使用或售后要求，请以商品详情和客服说明为准。',
  },
  sevenDay: {
    label: '含七天无理由说明',
    content: '1. 符合商城规则且商品完好的，可在商城后台当前配置的售后期限内申请无理由退货，起算方式以订单提示为准。\n2. 影响二次销售、定制、拆封或另有页面说明的商品，可能不适用七天无理由退货。\n3. 质量问题、错发漏发等情况请保留商品、包装和凭证，联系客服处理。\n4. 退款金额及退货运费承担方式以售后审核结果为准。',
  },
  quality: {
    label: '质量问题售后说明',
    content: '1. 收到商品后请及时检查，发现质量问题请拍照或录制视频并联系客服。\n2. 经核实属于商品质量、错发或漏发的，商城将按售后规则协助处理。\n3. 非质量问题的退换货，请先确认商品是否符合页面标注的退换条件。\n4. 退款金额和运费承担方式以订单实际情况及审核结果为准。',
  },
}
const afterSalePresetOptions = Object.entries(afterSalePolicyPresets).map(([value, preset]) => ({ value, label: preset.label }))
const defaultAfterSalePresetKey = 'basic'
const afterSaleSummaryLabel = computed(() => {
  const key = form.value.afterSalePresetKey
  if (key === 'custom') return '自定义售后说明'
  return afterSalePolicyPresets[key]?.label || '商城通用售后说明'
})
const afterSalePreviewText = computed(() => {
  const policy = form.value.afterSalePolicy || ''
  const firstLine = policy.split('\n')[0]?.trim() || ''
  return firstLine.length > 60 ? firstLine.slice(0, 60) + '...' : firstLine || '暂无内容'
})
const getGuaranteeIcon = (iconName) => {
  const iconMap = { shield: 'CircleCheck', return: 'RefreshLeft', package: 'Box', refund: 'Money', ban: 'CircleClose', truck: 'Van', heart: 'Star', badge: 'Medal' }
  return iconMap[iconName] || 'CircleCheck'
}
const addCustomGuarantee = () => {
  customGuaranteeForm.value = { enabled: true, presetKey: 'custom', icon: 'shield', title: '', description: '' }
  customGuaranteeEditIndex.value = -1
  customGuaranteeVisible.value = true
}
const editCustomGuarantee = (index) => {
  const item = form.value.serviceGuarantees[index]
  customGuaranteeForm.value = { ...item }
  customGuaranteeEditIndex.value = index
  customGuaranteeVisible.value = true
}
const saveCustomGuarantee = () => {
  const { title, description } = customGuaranteeForm.value
  if (!title?.trim()) return ElMessage.warning('请输入保障名称')
  if (!description?.trim()) return ElMessage.warning('请输入保障说明')
  if (customGuaranteeEditIndex.value >= 0) {
    form.value.serviceGuarantees[customGuaranteeEditIndex.value] = { ...customGuaranteeForm.value }
  } else {
    form.value.serviceGuarantees.push({ ...customGuaranteeForm.value })
  }
  customGuaranteeVisible.value = false
  ElMessage.success('保障已保存')
}
const removeCustomGuarantee = () => {
  form.value.serviceGuarantees.splice(customGuaranteeEditIndex.value, 1)
  customGuaranteeVisible.value = false
  ElMessage.success('保障已删除')
}
const parseArray = (value) => {
  if (Array.isArray(value)) return value
  try { const parsed = JSON.parse(value || '[]'); return Array.isArray(parsed) ? parsed : [] } catch { return [] }
}

const normalizeServiceGuarantees = (value) => parseArray(value).map((item) => {
  if (typeof item === 'string') {
    const preset = guaranteeDefaults[item] || {}
    return { enabled: true, presetKey: guaranteeDefaults[item] ? item : 'custom', icon: preset.icon || 'shield', title: item, description: preset.description || '以商城售后规则及商品实际情况为准。' }
  }
  const title = item?.title || ''
  const presetKey = item?.presetKey || (guaranteeDefaults[title] ? title : 'custom')
  return { enabled: item?.enabled !== false, presetKey, icon: item?.icon || 'shield', title, description: item?.description || '' }
})
const parseSkuAttributes = (value) => {
  try {
    const parsed = JSON.parse(value || '{}')
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') return []
    return Object.entries(parsed).map(([name, attributeValue]) => ({ name, value: String(attributeValue ?? '') }))
  } catch {
    return []
  }
}
const serializeSkuAttributes = (attributes = []) => JSON.stringify(Object.fromEntries(
  attributes
    .map((item) => [item.name?.trim(), item.value?.trim()])
    .filter(([name, value]) => name && value),
))
const defaultServiceGuarantees = () => Object.entries(guaranteeDefaults).map(([title, preset]) => ({
  enabled: false, presetKey: title, icon: preset.icon, title, description: preset.description,
}))

const inferAfterSalePreset = (policy) => Object.entries(afterSalePolicyPresets).find(([, preset]) => preset.content === policy)?.[0] || 'custom'
const defaultForm = () => ({ tenantId: 1, merchantId: null, merchantName: '', settlementCostChangeReason: '', settlementDelayMode: 'DEFAULT', settlementDelayDaysOverride: null, productNo: '', productName: '', subtitle: '', categoryName: '', mainImages: [], salePrice: 0, marketPrice: 0, costAmount: 0, pvValue: 0, bvValue: 0, stock: 0, safetyStock: 0, purchaseLimit: 0, normalSaleEnabled: 1, repurchaseSaleEnabled: 0, enrollmentSaleEnabled: 0, teamBonusMode: 'INHERIT', repurchasePrice: 0, repurchasePv: 0, repurchasePurchaseLimit: 0, salesCount: 0, sort: 0, status: 1, freightType: 0, freightAmount: 0, freeShippingAmount: 0, freightTemplateId: null, freightTemplateName: '', deliveryAddress: '', deliveryProvince: '', deliveryCity: '', deliveryDistrict: '', shippingAddressId: null, returnAddressId: null, deliveryTime: '48小时内发货', afterSalePresetKey: defaultAfterSalePresetKey, afterSalePolicy: afterSalePolicyPresets[defaultAfterSalePresetKey].content, serviceGuarantees: defaultServiceGuarantees(), detail: '', detailImageUrls: [] })

const changeMerchant = (merchantId) => { form.value.teamBonusMode = merchantId ? 'NONE' : 'INHERIT'; form.value.settlementDelayMode = 'DEFAULT'; form.value.settlementDelayDaysOverride = null }
const changeSettlementDelayMode = (mode) => { if (mode === 'DEFAULT') form.value.settlementDelayDaysOverride = null; else if (form.value.settlementDelayDaysOverride == null) form.value.settlementDelayDaysOverride = selectedMerchantDefaultDays.value }

const fetchData = async () => {
  const validation = validateSearchKeyword(query.value.keyword, { label: '商品关键词' })
  if (!validation.valid) {
    tableData.value = []
    pagination.value.total = 0
    searchFeedback.value = validation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.value.keyword = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = validation.keyword
    ? `未找到与“${validation.keyword}”匹配的商品`
    : '暂无商品'
  loading.value = true
  try {
    const res = await listShopProducts({ ...query.value, pageNum: pagination.value.page, pageSize: pagination.value.size })
    tableData.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally { loading.value = false }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchData()
}

const fetchCategories = async () => {
  const res = await listShopCategories({ status: 1 })
  categories.value = res.data || []
}

const openQuickCategory = () => {
  quickCategoryForm.value = { tenantId: 1, categoryName: '', sort: 0, status: 1, remark: '' }
  quickCategoryVisible.value = true
}

const saveQuickCategory = async () => {
  const categoryName = quickCategoryForm.value.categoryName?.trim()
  if (!categoryName) return ElMessage.warning('请输入分类名称')
  if (categories.value.some((item) => item.categoryName?.toLowerCase() === categoryName.toLowerCase())) return ElMessage.warning('该分类已经存在，请直接选择')
  quickCategorySaving.value = true
  try {
    const res = await createShopCategory({ ...quickCategoryForm.value, categoryName })
    await fetchCategories()
    form.value.categoryName = res.data?.categoryName || categoryName
    quickCategoryVisible.value = false
    ElMessage.success('分类已添加并自动选中')
  } finally { quickCategorySaving.value = false }
}

const fetchFreightTemplates = async () => {
  const res = await listFreightTemplates()
  freightTemplates.value = res.data || []
}

const addressOptionLabel = (item) => `${item.addressLabel || '未命名地址'}（${[item.province, item.city, item.district].filter(Boolean).join(' ')}）`
const fetchServiceAddresses = async () => {
  const res = await listShopServiceAddresses({ tenantId: 1 })
  const list = res.data || []
  shippingAddresses.value = list.filter((item) => Number(item.addressType) === 1)
  returnAddresses.value = list.filter((item) => Number(item.addressType) === 2)
}
const applyShippingAddress = (id) => {
  const address = shippingAddresses.value.find((item) => String(item.id) === String(id))
  if (!address) return
  deliveryRegion.value = [address.province, address.city, address.district].filter(Boolean)
  form.value.deliveryProvince = address.province
  form.value.deliveryCity = address.city
  form.value.deliveryDistrict = address.district
  form.value.deliveryAddress = [address.province, address.city, address.district, address.detailAddress].filter(Boolean).join(' ')
}

const fetchProductSettings = async () => {
  try {
    const res = await getProductSettings()
    performanceUnitsEnabled.value = Number(res.data?.showPv ?? 1) === 1
    clearDisabledPvValues()
  } catch { performanceUnitsEnabled.value = true }
}

const clearDisabledPvValues = () => {
  if (performanceUnitsEnabled.value) return
  tableData.value = tableData.value.map((item) => ({ ...item, pvValue: 0 }))
  if (form.value && Object.keys(form.value).length) form.value.pvValue = 0
  skuRows.value.forEach((item) => { item.pvValue = 0 })
}

const changePvSetting = async (enabled) => {
  try {
    await updateProductPvSetting(enabled)
    performanceUnitsEnabled.value = Boolean(enabled)
    clearDisabledPvValues()
    ElMessage.success(enabled ? '已开启商品 PV 填写' : '已关闭商品 PV 填写，商品按 PV=0 处理')
  } catch { performanceUnitsEnabled.value = !enabled }
}

const resetQuery = () => { query.value = { keyword: '', categoryName: '', status: null }; query.value.stockStatus = null; pagination.value.page = 1; fetchData() }
const stockState = (row) => {
  const stock = Number(row.stock || 0)
  const safetyStock = Number(row.safetyStock || 0)
  if (stock <= 0) return { label: '已缺货', type: 'danger' }
  if (stock <= safetyStock && safetyStock > 0) return { label: '低库存', type: 'warning' }
  return { label: '正常', type: 'success' }
}
const scrollToSection = (id) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
const batchSetStatus = async (status) => {
  if (!selectedRows.value.length) return
  const action = status === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定将已选择的 ${selectedRows.value.length} 个商品批量${action}吗？`, `批量${action}`, { type: 'warning' })
  } catch {
    return
  }
  batchLoading.value = true
  try {
    await Promise.all(selectedRows.value.map((row) => updateShopProductStatus(row.id, status)))
    ElMessage.success(`已批量${action} ${selectedRows.value.length} 个商品`)
    selectedRows.value = []
    await fetchData()
  } finally {
    batchLoading.value = false
  }
}
const openDialog = async (row) => {
  const mainImages = row ? [row.coverUrl, ...parseArray(row.galleryUrls)].filter(Boolean).slice(0, 5) : []
  form.value = row ? { ...defaultForm(), ...row, settlementDelayMode: row.settlementDelayDaysOverride == null ? 'DEFAULT' : 'OVERRIDE', mainImages, detailImageUrls: parseArray(row.detailImages), serviceGuarantees: normalizeServiceGuarantees(row.serviceTags), freightType: row.freightType ?? 0, pvValue: Number(row.pvValue || 0), afterSalePolicy: row.afterSalePolicy?.trim() || afterSalePolicyPresets[defaultAfterSalePresetKey].content, afterSalePresetKey: inferAfterSalePreset(row.afterSalePolicy?.trim() || afterSalePolicyPresets[defaultAfterSalePresetKey].content) } : { ...defaultForm(), shippingAddressId: shippingAddresses.value.find((item) => Number(item.isDefault) === 1)?.id || shippingAddresses.value[0]?.id || null, returnAddressId: returnAddresses.value.find((item) => Number(item.isDefault) === 1)?.id || returnAddresses.value[0]?.id || null }
  if (isMerchantUser.value) {
    form.value.merchantId = store.userInfo.merchantId
    form.value.merchantName = store.userInfo.merchantName || ''
    form.value.status = 0
    form.value.teamBonusMode = row?.teamBonusMode || 'NONE'
  }
  deliveryRegion.value = row?.deliveryProvince && row?.deliveryCity && row?.deliveryDistrict
    ? [row.deliveryProvince, row.deliveryCity, row.deliveryDistrict]
    : []
  if (form.value.shippingAddressId && !deliveryRegion.value.length) applyShippingAddress(form.value.shippingAddressId)
  skuRows.value = []
  removedSkuIds.value = []
  dialogVisible.value = true
  if (row?.id) {
    dialogLoading.value = true
    try {
      const res = await listShopSkus(row.id)
      skuRows.value = (res.data || []).map((item) => ({ ...item, safetyStock: Number(item.safetyStock || 0), attributes: parseSkuAttributes(item.attrsJson) }))
    } finally { dialogLoading.value = false }
  }
  clearDisabledPvValues()
  productSavedSnapshot.value = productEditSnapshot.value
}

const confirmCloseProductDialog = async (done) => {
  if (!hasUnsavedProduct.value) {
    done()
    return
  }
  try {
    await ElMessageBox.confirm('商品资料、规格或配送设置尚未保存，确定关闭吗？', '未保存的修改', {
      type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑',
    })
    productSavedSnapshot.value = ''
    done()
  } catch {
    // 继续编辑。
  }
}

const editProduct = async (row) => {
  if (row.merchantId && row.status === 1) {
    await ElMessageBox.confirm('修改商户商品前必须先下架。确认现在下架并进入编辑？', '先下架再修改', { type: 'warning' })
    await updateShopProductStatus(row.id, 0)
    row = { ...row, status: 0 }
    await fetchData()
  }
  await openDialog(row)
}

const uploadFile = async (file) => {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error(`图片大小为 ${(file.size / 1024 / 1024).toFixed(2)}MB，单张不能超过5MB`)
    throw new Error('单张图片不能超过5MB')
  }
  return (await uploadShopImage(file)).data
}
const uploadImageTo = async (field, file, max) => {
  if ((form.value[field] || []).length >= max) return ElMessage.warning(`最多上传 ${max} 张图片`)
  const url = await uploadFile(file)
  if (form.value[field].length >= max) return ElMessage.warning(`最多上传 ${max} 张图片，多余图片未加入商品`)
  form.value[field].push(url)
  ElMessage.success('图片上传成功')
}
const uploadSkuImage = async (row, file) => { row.imageUrl = await uploadFile(file); ElMessage.success('规格图上传成功') }
const removeImage = (field, index) => { form.value[field].splice(index, 1) }
const setCover = (index) => { const [image] = form.value.mainImages.splice(index, 1); form.value.mainImages.unshift(image) }

const addSku = () => skuRows.value.push({ skuNo: '', skuName: '', attributes: [], imageUrl: '', salePrice: Number(form.value.salePrice || 0), marketPrice: Number(form.value.marketPrice || 0), costAmount: Number(form.value.costAmount || 0), pvValue: 0, repurchasePrice: null, repurchasePv: null, bvValue: 0, stock: 0, safetyStock: 0, status: 1 })
const removeSku = (index) => { const row = skuRows.value[index]; if (row.id) removedSkuIds.value.push(row.id); skuRows.value.splice(index, 1) }
const changeProductType = async (type) => {
  if (type === 'MULTI') {
    if (!skuRows.value.length) addSku()
    return
  }
  if (!skuRows.value.length) return
  try {
    await ElMessageBox.confirm(
      '切换为单规格商品后，现有SKU将被删除，商品改用一组价格和库存。是否继续？',
      '确认切换商品类型',
      { type: 'warning', confirmButtonText: '切换为单规格', cancelButtonText: '取消' },
    )
    syncProductSummaryFromSkus()
    skuRows.value.forEach((row) => { if (row.id) removedSkuIds.value.push(row.id) })
    skuRows.value = []
  } catch {
    // 取消后继续保留多规格及其全部数据。
  }
}
const openSkuAttributes = (row, index) => {
  editingSkuIndex.value = index
  skuAttributeForm.value = (row.attributes || []).map((item) => ({ ...item }))
  if (!skuAttributeForm.value.length) skuAttributeForm.value.push({ name: '', value: '' })
  skuAttributeVisible.value = true
}
const saveSkuAttributes = () => {
  const normalized = skuAttributeForm.value
    .map((item) => ({ name: item.name?.trim(), value: item.value?.trim() }))
    .filter((item) => item.name || item.value)
  if (normalized.some((item) => !item.name || !item.value)) return ElMessage.warning('属性名和属性值必须成对填写')
  if (new Set(normalized.map((item) => item.name)).size !== normalized.length) return ElMessage.warning('同一个SKU不能重复添加相同属性名')
  skuRows.value[editingSkuIndex.value].attributes = normalized
  skuAttributeVisible.value = false
}
const syncProductSummaryFromSkus = () => {
  if (!hasSku.value) return
  const enabled = skuRows.value.filter((item) => Number(item.status) === 1)
  const values = (field, positiveOnly = false) => enabled
    .map((item) => Number(item[field] || 0))
    .filter((value) => Number.isFinite(value) && (!positiveOnly || value > 0))
  const salePrices = values('salePrice')
  const marketPrices = values('marketPrice', true)
  const costs = values('costAmount')
  form.value.salePrice = salePrices.length ? Math.min(...salePrices) : 0
  form.value.marketPrice = marketPrices.length ? Math.min(...marketPrices) : 0
  form.value.costAmount = costs.length ? Math.min(...costs) : 0
  form.value.stock = enabled.reduce((sum, item) => sum + Math.max(0, Number(item.stock || 0)), 0)
}
const applyAfterSalePreset = (presetKey) => {
  const preset = afterSalePolicyPresets[presetKey]
  if (preset) form.value.afterSalePolicy = preset.content
}

const parseFreightRules = (value) => {
  try { const parsed = JSON.parse(value || '[]'); return Array.isArray(parsed) ? parsed : [] } catch { return [] }
}
const expandFreightPath = (path = []) => {
  if (!Array.isArray(path) || !path.length) return []
  let options = pcaTextArr
  const matched = []
  for (const value of path) {
    const node = options.find((item) => item.value === value)
    if (!node) return [path]
    matched.push(node.value)
    options = node.children || []
  }
  const collectLeaves = (nodes, prefix) => nodes.flatMap((node) => {
    const next = [...prefix, node.value]
    return node.children?.length ? collectLeaves(node.children, next) : [next]
  })
  return options.length ? collectLeaves(options, matched) : [matched]
}
const normalizeFreightRulesForEditor = (rules = []) => rules.map((rule) => {
  const paths = (rule.regionPaths || []).flatMap(expandFreightPath)
  const uniquePaths = [...new Map(paths.map((path) => [path.join('\u0001'), path])).values()]
  return { ...rule, regionPaths: uniquePaths, freightAmount: Number(rule.freightAmount || 0) }
})
const openFreightTemplate = (template = null) => {
  freightTemplateForm.value = template
    ? { id: template.id, tenantId: template.tenantId || 1, templateName: template.templateName, defaultMode: template.defaultMode || 'FREE', defaultFreightAmount: Number(template.defaultFreightAmount || 0), status: template.status, rules: normalizeFreightRulesForEditor(parseFreightRules(template.rulesJson)) }
    : defaultFreightTemplateForm()
  freightTemplateDialogVisible.value = true
}
const editSelectedFreightTemplate = () => openFreightTemplate(freightTemplates.value.find((item) => item.id === form.value.freightTemplateId))
const addFreightRule = () => freightTemplateForm.value.rules.push({ regionPaths: [], mode: 'FREE', freightAmount: 0 })
const saveFreightTemplateForm = async () => {
  if (!freightTemplateForm.value.templateName?.trim()) return ElMessage.warning('请填写模板名称')
  if (freightTemplateForm.value.rules.some((rule) => !rule.regionPaths?.length)) return ElMessage.warning('请为每条特例选择地区')
  freightTemplateSaving.value = true
  try {
    const data = { ...freightTemplateForm.value }
    const res = data.id ? await updateFreightTemplate(data.id, data) : await createFreightTemplate(data)
    await fetchFreightTemplates()
    form.value.freightTemplateId = res.data?.id
    freightTemplateDialogVisible.value = false
    ElMessage.success('运费模板已保存')
  } finally { freightTemplateSaving.value = false }
}

const submitForm = async () => {
  if (!form.value.productName?.trim()) return ElMessage.warning('请输入商品名称')
  if (!form.value.mainImages.length) return ElMessage.warning('请至少上传一张商品主图')
  if (deliveryRegion.value.length !== 3) return ElMessage.warning('请选择完整的发货省、市、区/县')
  if (form.value.freightType === 1 && Number(form.value.freightAmount || 0) <= 0) return ElMessage.warning('固定运费必须大于0')
  if (form.value.freightType === 2 && Number(form.value.freeShippingAmount || 0) <= 0) return ElMessage.warning('请填写满额包邮门槛')
  if (Number(form.value.purchaseLimit || 0) < 0 || !Number.isInteger(Number(form.value.purchaseLimit || 0))) return ElMessage.warning('会员限购数量必须是大于等于0的整数')
  if (Number(form.value.normalSaleEnabled) !== 1 && Number(form.value.repurchaseSaleEnabled) !== 1 && Number(form.value.enrollmentSaleEnabled) !== 1) return ElMessage.warning('商品至少选择一个销售渠道')
  if (form.value.merchantId && form.value.teamBonusMode === 'INHERIT') return ElMessage.warning('商户商品必须明确选择团队奖金模式')
  if (form.value.merchantId && form.value.teamBonusMode === 'STANDARD' && Number(form.value.normalSaleEnabled) === 1) return ElMessage.warning('产生团队奖金的商户商品不能同时进入普通商城')
  if (form.value.merchantId && Number(form.value.costAmount || 0) <= 0) return ElMessage.warning('商户商品必须填写大于0的结算价')
  if (form.value.settlementDelayMode === 'OVERRIDE' && (!Number.isInteger(Number(form.value.settlementDelayDaysOverride)) || Number(form.value.settlementDelayDaysOverride) < 0 || Number(form.value.settlementDelayDaysOverride) > 365)) return ElMessage.warning('商品结算等待天数必须是0到365之间的整数')
  if (Number(form.value.repurchaseSaleEnabled) === 1 && Number(form.value.repurchasePrice || 0) <= 0) return ElMessage.warning('启用复购商城后请填写复购价')
  if (Number(form.value.repurchasePv || 0) > Number(form.value.repurchasePrice || 0)) return ElMessage.warning('复购PV不能超过复购价')
  if (skuRows.value.some((item) => Number(item.repurchasePv || 0) > Number(item.repurchasePrice || form.value.repurchasePrice || 0))) return ElMessage.warning('SKU复购PV不能超过对应复购价')
  if (Number(form.value.safetyStock || 0) < 0 || !Number.isInteger(Number(form.value.safetyStock || 0))) return ElMessage.warning('商品安全库存必须是大于等于0的整数')
  if (skuRows.value.some((item) => Number(item.safetyStock || 0) < 0 || !Number.isInteger(Number(item.safetyStock || 0)))) return ElMessage.warning('SKU安全库存必须是大于等于0的整数')
  if (form.value.freightType === 3 && !form.value.freightTemplateId) return ElMessage.warning('请选择运费模板')
  if (skuRows.value.some((item) => !item.skuName?.trim())) return ElMessage.warning('请填写所有 SKU 的规格名称')
  if (skuRows.value.some((item) => !item.attributes?.length)) return ElMessage.warning('请为每个SKU设置规格属性')
  if (hasSku.value && !skuRows.value.some((item) => Number(item.status) === 1)) return ElMessage.warning('多规格商品至少需要启用一个SKU')
  if (form.value.serviceGuarantees.some((item) => item.enabled && !item.presetKey)) return ElMessage.warning('请选择服务保障预设，或切换为自定义保障')
  if (form.value.serviceGuarantees.some((item) => item.enabled && !item.title?.trim())) return ElMessage.warning('请填写已勾选服务保障的标题')
  if (form.value.serviceGuarantees.some((item) => item.enabled && !item.description?.trim())) return ElMessage.warning('请填写已勾选服务保障的详细介绍')
  if (!form.value.afterSalePolicy?.trim()) return ElMessage.warning('请选择售后模板或填写售后说明')
  syncProductSummaryFromSkus()
  // 保存前重新读取开关，避免旧后台页面缓存了“开启 PV”的状态而拦截低价商品。
  try {
    const latestSettings = await getProductSettings()
    performanceUnitsEnabled.value = Number(latestSettings.data?.showPv ?? 1) === 1
    if (!performanceUnitsEnabled.value) clearDisabledPvValues()
  } catch {
    // 读取失败时保留当前开关状态；后端仍会按服务端配置做最终校验。
  }
  if (performanceUnitsEnabled.value) {
    if (Number(form.value.pvValue || 0) > productPvLimit.value) return ElMessage.warning(`${hasSku.value ? '默认单件PV' : '单件PV'}不能超过销售价 ${productPvLimit.value.toFixed(2)}`)
    const invalidSku = skuRows.value.find((item) => Number(item.pvValue || 0) > Math.max(0, Number(item.salePrice || 0)))
    if (invalidSku) return ElMessage.warning(`SKU“${invalidSku.skuName || '未命名'}”的单件PV不能超过其销售价`)
  } else {
    // 关闭商品 PV 后，隐藏字段中的历史值不应阻止低价商品保存，也不应继续进入订单快照。
    form.value.pvValue = 0
    skuRows.value.forEach((item) => { item.pvValue = 0 })
  }
  submitting.value = true
  try {
    const [deliveryProvince, deliveryCity, deliveryDistrict] = deliveryRegion.value
  const serviceTags = form.value.serviceGuarantees
      .filter((item) => item.title?.trim())
      .map((item) => ({ enabled: Boolean(item.enabled), icon: item.icon || 'shield', title: item.title.trim(), description: item.description?.trim() || '' }))
    const payload = { ...form.value, productName: form.value.productName.trim(), subtitle: form.value.subtitle?.trim() || null, categoryName: form.value.categoryName?.trim() || null, deliveryProvince, deliveryCity, deliveryDistrict, deliveryAddress: form.value.shippingAddressId ? form.value.deliveryAddress : deliveryRegion.value.join(' '), coverUrl: form.value.mainImages[0], galleryUrls: JSON.stringify(form.value.mainImages.slice(1)), detailImages: JSON.stringify(form.value.detailImageUrls), serviceTags: JSON.stringify(serviceTags), bvValue: 0 }
    delete payload.mainImages; delete payload.detailImageUrls; delete payload.serviceGuarantees; delete payload.afterSalePresetKey; delete payload.settlementDelayMode
    await publishShopProduct(form.value.id, {
      product: payload,
      skus: skuRows.value.map((sku) => {
        const { attributes, ...data } = sku
        return { ...data, attrsJson: serializeSkuAttributes(attributes), pvValue: Number(data.pvValue || 0), bvValue: 0 }
      }),
      removedSkuIds: removedSkuIds.value,
    })
    ElMessage.success(form.value.merchantId ? '商品已保存为下架草稿，请在列表提交审核' : '商品、图片、规格和配送信息已保存')
    productSavedSnapshot.value = productEditSnapshot.value
    dialogVisible.value = false
    await fetchData()
  } finally { submitting.value = false }
}

const toggleStatus = async (row) => {
  if (row.status === 1 || !row.merchantId || row.merchantReviewStatus === 'APPROVED') {
    await updateShopProductStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(row.status === 1 ? '商品已下架' : '商品已重新上架')
  } else {
    await ElMessageBox.confirm(`提交“${row.productName}”审核？审核人员将确认销售价和结算价，通过后自动上架。`, '提交商品审核', { type: 'warning' })
    await submitMerchantProductReview(row.id)
    ElMessage.success('已提交审核，请等待平台处理')
  }
  fetchData()
}

onMounted(async () => {
  const merchantRes = await listMerchants({ status: 1 })
  merchants.value = merchantRes.data || []
  await Promise.all([fetchData(), fetchCategories(), fetchProductSettings(), fetchFreightTemplates(), fetchServiceAddresses()])
})
</script>

<style lang="scss" scoped>
.page-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:16px; padding:4px 2px 0; h2{margin:0;color:#303133;font-size:22px;line-height:1.35} p{margin:6px 0 0;color:#909399;font-size:13px} }
.product-cell { display:flex; align-items:center; gap:12px; min-width:0; }
.search-feedback { margin-bottom:16px; }
.batch-toolbar { display:flex; align-items:center; gap:10px; margin:0 0 14px; padding:10px 14px; border:1px solid #d9ecff; border-radius:8px; background:#f2f8ff; color:#409eff; font-size:13px; }
.batch-toolbar span { margin-right:auto; font-weight:600; }
.stock-cell { display:flex; align-items:center; gap:7px; }
.stock-cell strong { color:#303133; font-size:14px; }
.stock-help { margin-top:4px; color:#a8abb2; font-size:12px; }
.cover,.cover-fallback { width:60px; height:60px; border-radius:8px; background:#f5f7fa; flex:0 0 auto; }
.cover-fallback { display:flex; align-items:center; justify-content:center; color:#909399; }
.product-meta { min-width:0; .name{font-weight:600;color:#303133;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.sub{color:#909399;font-size:12px;line-height:18px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap} }
.muted-value { color:#c0c4cc; }
.merchant-settlement { color:#1b6f3a; }
.pv-invalid { color:#f56c6c; font-weight:700; }
.pv-warning { margin-left:5px; color:#e6a23c; vertical-align:-2px; cursor:help; }
.column-help { margin-left:5px; color:#909399; vertical-align:-2px; cursor:help; }
.pv-global-setting { display:flex; align-items:center; justify-content:space-between; gap:20px; border-top:1px solid #ebeef5; padding:14px 4px 0; color:#303133; span{margin-left:12px;color:#909399;font-size:13px} }
.publish-shell { max-width:1440px; margin:0 auto; padding:0 18px 90px; }
.publish-layout { display:grid; grid-template-columns:150px minmax(0,1fr); gap:18px; align-items:start; }
.publish-nav { position:sticky; top:18px; display:flex; flex-direction:column; gap:6px; padding:10px 0; }
.publish-nav button { border:0; border-left:3px solid transparent; padding:10px 12px; background:transparent; color:#606266; text-align:left; border-radius:0 7px 7px 0; cursor:pointer; font-size:13px; transition:all .2s; }
.publish-nav button:hover { color:#409eff; background:#f2f8ff; border-left-color:#409eff; }
.publish-form { margin-top:18px; }
.form-section { background:#fff; border:1px solid #e4e7ed; border-radius:10px; padding:20px 24px; margin-bottom:16px; box-shadow:0 1px 3px rgba(0,0,0,.025); h3{font-size:17px;margin:0 0 20px;color:#303133;border-left:4px solid #409eff;padding-left:10px} }
.section-title { display:flex; justify-content:space-between; align-items:center; margin-bottom:18px; h3{margin-bottom:0} }
.image-manager { display:flex; flex-wrap:wrap; gap:12px; width:100%; }
.image-tile,.image-uploader { position:relative; width:138px; height:138px; border:1px dashed #c0ccda; border-radius:8px; overflow:hidden; background:#fafafa; }
.image-tile :deep(.el-image) { width:100%; height:100%; }
.image-uploader { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; color:#909399; cursor:pointer; font-size:13px; &:hover{border-color:#409eff;color:#409eff} .el-icon{font-size:26px} }
.cover-badge,.image-order { position:absolute; left:0; top:0; padding:3px 8px; color:#fff; background:#409eff; border-radius:0 0 6px 0; font-size:12px; }
.image-order { background:rgba(0,0,0,.55); }
.image-actions { position:absolute; inset:auto 0 0; display:flex; justify-content:center; gap:12px; padding:8px; background:rgba(0,0,0,.6); button{border:0;background:none;color:#fff;cursor:pointer;padding:0} }
.detail-tile { width:116px; height:154px; }
.field-help { width:100%; color:#909399; font-size:12px; line-height:20px; margin-top:7px; }
.money-input { width:100%; }
.pv-row { margin-top:20px; }
.product-type-title { align-items:flex-start; }
.sku-toolbar { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:4px 0 14px; color:#606266; font-size:13px; }
.multi-pv-row { margin-top:0; }
.cost-help { margin-top:16px; }
.sku-table-wrap { overflow-x:auto; }
.sku-attributes { display:flex; align-items:center; flex-wrap:wrap; gap:6px; }
.empty-attribute { color:#909399; font-size:12px; }
.attribute-row { display:grid; grid-template-columns:1fr 1fr auto; gap:10px; margin-bottom:10px; }
.template-picker { display:flex; align-items:center; gap:10px; flex-wrap:wrap; width:100%; }
.freight-region-cascader :deep(.el-input__wrapper) { min-height:42px; height:auto; align-items:flex-start; padding-top:5px; padding-bottom:5px; }
.freight-region-cascader :deep(.el-cascader__tags) { position:static; transform:none; max-height:148px; padding:0; overflow:auto; flex-wrap:wrap; }
.freight-region-cascader :deep(.el-tag) { max-width:100%; height:auto; min-height:24px; white-space:normal; line-height:18px; }
.category-picker { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:8px; width:100%; }
.quick-help { margin-left:10px; color:#909399; font-size:12px; }
.guarantee-tags { width:100%; }
.guarantee-tags-list { display:flex; flex-wrap:wrap; gap:10px; align-items:center; }
.guarantee-tag { cursor:pointer; transition:all .2s; display:inline-flex; align-items:center; gap:6px; }
.guarantee-tag .el-icon { font-size:14px; }
.guarantee-tag:hover { transform:translateY(-1px); }
.add-custom-btn { margin-left:4px; }
.guarantee-tags-help { color:#909399; font-size:12px; margin-top:8px; }
.after-sale-compact { width:100%; }
.after-sale-summary { display:flex; align-items:center; justify-content:space-between; padding:12px 16px; background:#f8fafc; border:1px solid #e4e7ed; border-radius:8px; cursor:pointer; transition:all .2s; }
.after-sale-summary:hover { border-color:#409eff; background:#f0f7ff; }
.after-sale-summary-left { display:flex; align-items:center; gap:10px; min-width:0; flex:1; }
.after-sale-summary-left .el-icon { color:#409eff; font-size:18px; flex-shrink:0; }
.after-sale-summary-label { font-weight:600; color:#303133; flex-shrink:0; }
.after-sale-summary-preview { color:#909399; font-size:13px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.after-sale-expand-icon { color:#909399; transition:transform .3s; flex-shrink:0; }
.after-sale-expand-icon.expanded { transform:rotate(180deg); }
.after-sale-detail { margin-top:12px; padding:16px; background:#fafafa; border-radius:8px; border:1px solid #ebeef5; }
.after-sale-detail-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; }
.after-sale-detail-hint { color:#909399; font-size:12px; }
.sku-image,.sku-image-placeholder { width:54px; height:54px; border-radius:5px; }
.sku-image-placeholder { display:flex;align-items:center;justify-content:center;border:1px dashed #c0ccda;color:#909399;cursor:pointer; }
.dialog-footer { position:fixed; z-index:20; left:0; right:0; bottom:0; display:flex; justify-content:flex-end; gap:10px; padding:14px 30px; background:#fff; border-top:1px solid #e4e7ed; box-shadow:0 -2px 8px rgba(0,0,0,.05); }
@media (max-width: 900px) { .page-heading{align-items:flex-start;flex-direction:column;gap:10px}.pv-global-setting{align-items:flex-start}.pv-global-setting span{display:block;margin:5px 0 0}.batch-toolbar{align-items:flex-start;flex-wrap:wrap}.batch-toolbar span{width:100%;margin-right:0}.publish-layout{display:block}.publish-nav{position:sticky;top:0;z-index:5;flex-direction:row;overflow:auto;padding:8px 0;background:#fff;border-bottom:1px solid #ebeef5}.publish-nav button{border-left:0;border-bottom:3px solid transparent;white-space:nowrap;border-radius:7px 7px 0 0}.publish-nav button:hover{border-left-color:transparent;border-bottom-color:#409eff}.form-section{padding:16px 12px}.product-type-title,.sku-toolbar{align-items:flex-start;flex-direction:column}.guarantee-tags-list{gap:8px}.after-sale-summary-left{flex-direction:column;align-items:flex-start;gap:4px}.after-sale-summary-preview{white-space:normal} }
</style>
