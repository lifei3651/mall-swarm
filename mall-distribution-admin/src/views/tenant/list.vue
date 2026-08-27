<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h2>商城视觉与页面</h2>
        <p>先选择整体版型，再配置品牌、模块和独立页面，最后预览发布。</p>
      </div>
    </div>

    <section v-loading="loading" class="current-decoration-card" aria-labelledby="current-decoration-title">
      <div v-if="tableData[0]" class="current-decoration-main">
        <div class="current-decoration-kicker">当前商城装修</div>
        <div class="current-decoration-brand">
          <span class="current-decoration-logo">
            <img v-if="tableData[0].logoUrl" :src="normalizeMediaUrl(tableData[0].logoUrl)" alt="" />
            <b v-else>{{ (tableData[0].brandName || tableData[0].tenantName || '灵启').slice(0, 1) }}</b>
          </span>
          <div>
            <h3 id="current-decoration-title">{{ tableData[0].brandName || tableData[0].tenantName || '灵启商城' }}</h3>
            <span class="current-decoration-theme"><i :style="{ backgroundColor: tableData[0].themeColor || '#e7193f' }"></i>{{ getTemplateName(tableData[0].productTemplate) }}</span>
          </div>
        </div>
        <div class="current-decoration-layout">
          <span>当前主版型</span>
          <strong>{{ currentLayoutSummary }}</strong>
          <el-tag size="small" :type="displayConfigLoaded ? 'success' : 'info'">{{ displayConfigLoaded ? '已发布' : '待配置' }}</el-tag>
        </div>
        <p>在一个工作台里完成版型、品牌、首页模块、独立页面和底部导航设置。</p>
        <div class="current-decoration-actions">
          <el-button type="primary" size="large" @click="openDisplayDialog(tableData[0])">进入装修工作台</el-button>
          <el-button size="large" @click="openVersionDialog(tableData[0])">版本记录</el-button>
        </div>
      </div>
      <div v-if="tableData[0]" class="current-decoration-preview" aria-label="当前商城结构预览">
        <div class="decoration-phone">
          <span class="decoration-phone-status">9:41</span>
          <div class="decoration-phone-brand"><i :style="{ backgroundColor: tableData[0].themeColor || '#e7193f' }"></i><strong>{{ tableData[0].brandName || tableData[0].tenantName || '灵启商城' }}</strong></div>
          <div class="decoration-phone-search">搜索商品</div>
          <span class="layout-template-preview current-layout-preview" :class="`preview-${currentDisplayConfig.layoutTemplate || 'standard'}`"><i></i><b></b><em></em><small></small></span>
          <div class="decoration-phone-nav"><span>首页</span><span>分类</span><span>购物车</span><span>我的</span></div>
        </div>
      </div>
      <el-empty v-else :image-size="72" description="暂无可装修的商城" />
    </section>

    <el-dialog v-model="displayDialogVisible" title="商城视觉装修工作台" width="min(1100px, calc(100vw - 56px))" top="12px" class="display-workbench-dialog" :before-close="confirmCloseDisplayDialog">
      <el-alert title="先选择整体版型，再逐步配置其他内容。修改会实时预览，点击“保存发布”后才会影响客户前台。" type="info" :closable="false" class="display-alert" />
      <div class="workbench-heading">
        <div><span>当前编辑</span><strong>{{ editSectionLabel }}</strong></div>
        <div class="workbench-heading-meta"><span class="draft-dot"></span>右侧预览实时更新</div>
      </div>
      <div class="display-workbench">
        <nav class="workbench-nav" aria-label="装修步骤">
          <button v-for="(group, index) in workbenchGroups" :key="group.key" type="button" :class="{ active: activeEditSection === group.key }" @click="activeEditSection = group.key">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <div><strong>{{ group.label }}</strong><small>{{ group.description }}</small></div>
          </button>
        </nav>
        <aside class="display-controls">
          <div class="display-section-switcher">
            <div class="display-section-brand-only"><strong>{{ editSectionLabel }}</strong><small>{{ activeWorkbenchGroup?.description }}</small></div>
          </div>
          <section v-if="activeEditSection === 'brand'" class="control-section visual-design-panel">
            <div class="control-section-heading">
              <div><strong>品牌视觉</strong><small>修改左侧内容，右侧手机会实时更新；保存发布后客户前台生效</small></div>
              <el-tag size="small" type="success">实时预览</el-tag>
            </div>
            <div class="theme-preset-grid compact-theme-grid">
              <button v-for="theme in themeOptions" :key="theme.value" type="button" class="theme-preset" :class="{ active: isThemePresetActive(displayForm, theme) }" @click.stop.prevent="applyDisplayTheme(theme)">
                <span class="theme-preview" :style="{ '--preview-color': theme.color, '--preview-radius': theme.radius }"><i></i><b></b><em></em></span>
                <strong>{{ theme.label }}</strong>
                <small>{{ theme.description }}</small>
              </button>
            </div>
            <div class="visual-design-fields">
              <div class="visual-design-field"><span>商城名称</span><el-input v-model="displayForm.brandName" maxlength="64" placeholder="客户前台展示名称" /></div>
              <div class="visual-design-field"><span>品牌 LOGO</span><div class="display-logo-editor"><el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadDisplayLogo"><div class="display-logo-uploader"><el-image v-if="displayForm.logoUrl && !displayLogoLoadFailed" :src="normalizeMediaUrl(displayForm.logoUrl)" fit="contain" @error="displayLogoLoadFailed = true" /><span v-else>{{ displayForm.logoUrl ? '重传' : '上传' }}</span></div></el-upload><small>建议透明 PNG，客户前台和浏览器标签页共用</small></div></div>
              <div class="visual-design-field"><span>主题色</span><div class="color-editor"><el-color-picker v-model="displayForm.themeColor" /><el-input v-model="displayForm.themeColor" maxlength="7" placeholder="#e7193f" /></div></div>
            </div>
            <div class="brand-color-detail">
              <div class="control-section-heading"><div><strong>颜色细节</strong><small>按需微调；留空时沿用当前主题</small></div><el-button type="primary" link @click="resetColors">恢复默认</el-button></div>
              <div class="color-grid">
                <label v-for="color in colorFields" :key="color.key"><span>{{ color.label }}</span><el-color-picker v-model="displayForm.colors[color.key]" show-alpha /></label>
              </div>
            </div>
          </section>
          <section v-if="activeEditSection === 'pages'" class="control-section independent-page-hub">
            <div class="control-section-heading"><div><strong>独立页面</strong><small>页面总开关与首页入口分层控制；关闭不删除已配置内容</small></div></div>
            <div class="independent-page-tabs" role="tablist" aria-label="选择独立页面">
              <button v-for="page in independentPages" :key="page.key" type="button" role="tab" :aria-selected="independentPageTab === page.key" :class="{ active: independentPageTab === page.key }" @click="independentPageTab = page.key"><span>{{ page.icon }}</span><div><strong>{{ page.label }}</strong><small>{{ page.description }}</small></div></button>
            </div>
          </section>
          <section v-if="activeEditSection === 'pages' && independentPageTab === 'culture'" class="control-section feature-control-section">
            <div class="control-section-heading"><div><strong>品牌文化页</strong><small>独立页面、独立开关；关闭后前台入口和内容均不公开</small></div><el-tag size="small" type="info">独立页面</el-tag></div>
            <div class="feature-toggle-card">
              <div class="feature-toggle-copy"><span class="feature-toggle-icon">文</span><div><strong>公开品牌文化页</strong><small>开启后可在“首页模块 → 轮播图片与跳转”添加品牌文化横幅入口；各商城端共用同一内容</small></div></div>
              <div class="feature-toggle-action"><span :class="{ enabled: displayForm.brandCultureEnabled === 1 }">{{ displayForm.brandCultureEnabled === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="displayForm.brandCultureEnabled" :active-value="1" :inactive-value="0" aria-label="开启或关闭品牌文化页" /></div>
            </div>
            <div class="culture-form">
              <div class="visual-design-field"><span>页面标题（可选）</span><el-input v-model="displayForm.brandCultureTitle" maxlength="80" show-word-limit placeholder="例如：关于我们" /></div>
              <div class="visual-design-field"><span>一句话介绍（可选）</span><el-input v-model="displayForm.brandCultureSubtitle" maxlength="200" show-word-limit placeholder="用于分享、搜索和图片加载失败时的说明" /></div>
              <el-alert title="首页入口横幅已统一放到“首页模块 → 轮播图片与跳转”管理，建议750×320px、JPG/PNG/WebP、单张≤3MB。这里不再重复维护页面封面。" type="info" :closable="false" show-icon />
              <el-alert v-if="displayForm.brandCultureEnabled === 1 && !displayForm.brandCultureDetailImages?.length" :title="brandCultureContentWarning" type="warning" :closable="false" show-icon />
              <div class="visual-design-field culture-detail-field">
                <span>品牌文化详情图</span>
                <div class="culture-detail-toolbar"><small>建议宽750px，单张高度1000–3000px；JPG/PNG/WebP；单张≤5MB，合计≤30MB，最多10张</small><el-button v-if="displayForm.brandCultureDetailImages?.length" type="danger" link @click="clearBrandCultureDetails">清空全部</el-button></div>
                <el-upload action="#" multiple :show-file-list="false" accept=".jpg,.jpeg,.png,.webp" :before-upload="beforeBrandCultureDetailUpload" :http-request="uploadBrandCultureDetail"><el-button type="primary" plain>选择详情图</el-button></el-upload>
                <div v-if="displayForm.brandCultureDetailImages?.length" class="culture-detail-list">
                  <div v-for="(image, index) in displayForm.brandCultureDetailImages" :key="`${image.url}-${index}`" class="culture-detail-item" draggable="true" @dragstart="brandCultureDraggingIndex = index" @dragover.prevent @drop="dropBrandCultureImage(index)" @dragend="brandCultureDraggingIndex = null">
                    <span class="culture-detail-handle" title="拖拽排序">⋮⋮</span><img :src="normalizeMediaUrl(image.url)" :alt="`详情图${index + 1}`" loading="lazy" /><div><strong>详情图 {{ index + 1 }}</strong><small>{{ formatFileSize(image.size) }}</small></div><el-button type="danger" link @click="removeBrandCultureDetail(index)">删除</el-button>
                  </div>
                </div>
                <small v-else class="culture-detail-empty">尚未上传详情图；真正的旧文字仍可兜底，图片文件名或图片地址不会作为正文显示。</small>
              </div>
            </div>
          </section>
          <section v-if="activeEditSection === 'layout'" class="control-section">
            <div class="control-section-heading"><div><strong>先选择商城大框架</strong><small>只改变排版，不改变模块开关、排序、品牌、独立页面或底部导航</small></div><el-tag size="small" type="info">第一步</el-tag></div>
            <div class="layout-template-grid">
              <button v-for="template in layoutTemplateOptions" :key="template.value" type="button" class="layout-template-card" :class="{ active: displayForm.layoutTemplate === template.value }" @click="applyLayoutTemplate(template)">
                <span class="layout-template-preview" :class="`preview-${template.value}`"><i></i><b></b><em></em><small></small></span>
                <strong>{{ template.label }}</strong>
                <small>{{ template.description }}</small>
              </button>
            </div>
            <div v-if="displayForm.layoutTemplate === 'category-focus'" class="category-guide-config">
              <div class="control-section-heading"><div><strong>再选择分类导购结构</strong><small>A、B、C 是分类导购版的下一层选择，原有模块值会一直保留</small></div><el-tag size="small" type="success">分类导购</el-tag></div>
              <div class="category-guide-template-grid">
                <button v-for="template in categoryGuideTemplateOptions" :key="template.value" type="button" :class="{ active: displayForm.categoryGuideTemplate === template.value }" @click="displayForm.categoryGuideTemplate = template.value"><strong>{{ template.label }}</strong><small>{{ template.description }}</small></button>
              </div>
            </div>
          </section>
          <section v-if="activeEditSection === 'pages' && independentPageTab === 'live'" class="control-section feature-control-section">
            <div class="control-section-heading"><div><strong>直播广场</strong><small>直播中、直播预告与直播详情共用完整页面总开关；首页卡片仍可单独隐藏</small></div><el-tag size="small" type="info">独立页面</el-tag></div>
            <div class="feature-toggle-card">
              <div class="feature-toggle-copy"><span class="feature-toggle-icon">◉</span><div><strong>直播广场完整页面总开关</strong><small>关闭后首页直播卡片、直播中、直播预告和直播详情均不公开，已配置直播间与会员预约继续保留</small></div></div>
              <div class="feature-toggle-action"><span :class="{ enabled: displayForm.liveSquareEnabled === 1 }">{{ displayForm.liveSquareEnabled === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="displayForm.liveSquareEnabled" :active-value="1" :inactive-value="0" aria-label="开启或关闭直播广场" /></div>
            </div>
            <p class="section-note">独立页面默认独立开关：今后新增单独业务页面时，必须同步提供客户级总开关、关闭后的直达保护和数据保留规则。</p>
          </section>
          <section v-if="activeEditSection === 'pages' && independentPageTab === 'newArrivals'" class="control-section feature-control-section">
            <div class="control-section-heading"><div><strong>新品速递</strong><small>新品完整页面与首页卡片分层控制，不影响普通商品列表</small></div><el-tag size="small" type="info">独立页面</el-tag></div>
            <div class="feature-toggle-card">
              <div class="feature-toggle-copy"><span class="feature-toggle-icon new-arrivals-icon">NEW</span><div><strong>新品完整页面总开关</strong><small>开启后才允许访问新品完整页面；首页卡片仍可在“首页模块”单独隐藏。关闭不会下架任何商品</small></div></div>
              <div class="feature-toggle-action"><span :class="{ enabled: displayForm.newArrivalsEnabled === 1 }">{{ displayForm.newArrivalsEnabled === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="displayForm.newArrivalsEnabled" :active-value="1" :inactive-value="0" aria-label="开启或关闭新品速递" /></div>
            </div>
            <div class="new-arrival-window-setting">
              <div><strong>自动新品展示时间</strong><small>商品首次正式上架后自动进入新品；商品中心还可额外追加其他在售商品</small></div>
              <el-radio-group :model-value="displayForm.newArrivalWindowDays === 0 ? 'PERMANENT' : 'TIMED'" @change="(mode) => { displayForm.newArrivalWindowDays = mode === 'PERMANENT' ? 0 : 30 }">
                <el-radio-button value="TIMED">按天数</el-radio-button><el-radio-button value="PERMANENT">永久</el-radio-button>
              </el-radio-group>
              <div v-if="displayForm.newArrivalWindowDays !== 0" class="new-arrival-days"><el-input-number v-model="displayForm.newArrivalWindowDays" :min="30" :max="365" :precision="0" /><span>天（30～365天）</span></div>
            </div>
          </section>
          <section v-if="activeEditSection === 'home'" class="control-section">
            <div class="control-section-heading"><div><strong>首页模块</strong><small>拖动调整顺序；直播与新品固定横排，相对顺序决定左右位置</small></div><el-tag size="small" type="info">实时预览</el-tag></div>
            <div class="module-list module-list-sortable">
              <div v-for="(module, index) in displayForm.homeModules" :key="module.type" class="module-item" draggable="true" @dragstart="startModuleDrag(index)" @dragover.prevent @drop="dropModule(index)" @dragend="draggingModuleIndex = null">
                <span class="drag-handle" aria-hidden="true">⋮⋮</span>
                <strong>{{ moduleNames[module.type] || module.type }}</strong>
                <div class="sort-actions" aria-label="调整模块顺序">
                  <el-button text size="small" :disabled="index === 0" @click="moveModule(index, -1)">上移</el-button>
                  <el-button text size="small" :disabled="index === displayForm.homeModules.length - 1" @click="moveModule(index, 1)">下移</el-button>
                </div>
                <el-switch
                  v-if="module.type === 'trust'"
                  :model-value="Number(displayForm.showTrustStrip) === 1"
                  :active-value="true"
                  :inactive-value="false"
                  active-text="展示"
                  inactive-text="隐藏"
                  @change="setTrustEnabled"
                />
                <div v-else-if="module.type === 'live' || module.type === 'newArrivals'" class="module-dependent-switch">
                  <small v-if="module.type === 'live' && displayForm.liveSquareEnabled !== 1">直播广场总开关已关闭，保留当前首页开关值</small>
                  <small v-if="module.type === 'newArrivals' && displayForm.newArrivalsEnabled !== 1">新品速递总开关已关闭，保留当前首页开关值</small>
                  <el-switch v-model="module.enabled" active-text="展示" inactive-text="隐藏" :disabled="(module.type === 'live' && displayForm.liveSquareEnabled !== 1) || (module.type === 'newArrivals' && displayForm.newArrivalsEnabled !== 1)" />
                </div>
                <el-switch v-else v-model="module.enabled" active-text="展示" inactive-text="隐藏" />
              </div>
            </div>
            <div v-if="displayForm.layoutTemplate === 'category-focus'" class="home-template-modules">
              <div class="control-section-heading"><div><strong>{{ selectedCategoryGuideLabel }}专属模块</strong><small>只显示当前子版型可用的模块；切换版型不会清空原值</small></div><el-tag size="small" type="success">当前版型</el-tag></div>
              <div class="guide-module-switches">
                <div v-for="module in selectedCategoryGuideModules" :key="module[0]"><span>{{ module[1] }}</span><el-switch v-model="displayForm[module[0]]" :active-value="1" :inactive-value="0" /></div>
              </div>
              <p v-if="directoryGuideInvalid" class="guide-module-error" role="alert">请至少开启一个模块，或切换其他首页版型</p>
            </div>
            <div class="home-category-settings">
              <div class="control-section-heading"><div><strong>首页分类内容</strong><small>控制分类模块整体与单个分类是否展示</small></div></div>
              <div class="control-switch-row"><span>首页显示分类</span><el-switch v-model="displayForm.showHomeCategories" :active-value="1" :inactive-value="0" /></div>
              <div class="category-list category-list-draft">
                <div v-for="category in categories" :key="category.id" class="category-row"><span>{{ category.categoryName }}</span><el-switch :model-value="categoryDraft[category.id] ?? 1" :active-value="1" :inactive-value="0" active-text="展示" inactive-text="隐藏" @change="(value) => setCategoryDraft(category, value)" /></div>
                <el-empty v-if="!categories.length" :image-size="44" description="暂无商品分类，可直接展示精选商品" />
              </div>
            </div>
            <div class="home-banner-settings">
              <div><strong>轮播图片与跳转</strong><small>当前 {{ previewBanners.length }} 条已启用；在同一工作台中打开管理</small></div>
              <el-button type="primary" plain @click="bannerDialogVisible = true">管理轮播图片</el-button>
            </div>
          </section>

          <section v-if="activeEditSection === 'nav'" class="control-section">
            <div class="control-section-heading"><div><strong>底部导航</strong><small>独立控制分类和订单入口，切换首页版型不会改动这里</small></div></div>
            <p class="section-note nav-scope-note">隐藏只会移除底栏入口：分类页、首页分类模块和“我的”中的订单都会继续保留。</p>
            <div class="nav-config-list nav-list-sortable">
              <div v-for="nav in configurableBottomNav" :key="nav.type" class="nav-config-row">
                <span class="nav-type-name">{{ navNames[nav.type] || nav.type }}</span>
                <el-input v-model="nav.label" maxlength="6" style="width:100px" />
                <el-switch v-model="nav.enabled" active-text="展示" inactive-text="隐藏" />
              </div>
            </div>
          </section>
        </aside>

        <section class="preview-stage">
          <div class="preview-stage-heading"><div><strong>客户手机版预览</strong><span>{{ isCulturePreview ? '品牌文化独立页面' : '首页模块与前台保持同一套配置' }}</span></div><el-tag type="success">草稿预览</el-tag></div>
          <div class="mobile-preview-shell live-mobile-preview" :class="`layout-preview-${displayForm.layoutTemplate || 'standard'}`" :style="previewStyle">
            <div class="mobile-preview-status"><span>9:41</span><span>● ● ●</span></div>
            <div v-if="!isCulturePreview" class="mobile-preview-brand"><span class="mobile-preview-logo"><img v-if="displayForm.logoUrl && !displayLogoLoadFailed" :src="normalizeMediaUrl(displayForm.logoUrl)" alt="" @error="displayLogoLoadFailed = true" /><span v-else>{{ (displayForm.brandName || '灵启').slice(0, 1) }}</span></span><strong>{{ displayForm.brandName || '灵启商城' }}</strong><span class="mobile-preview-share">分享</span></div>
            <div v-else class="mobile-preview-page-header"><span>‹</span><strong>{{ displayForm.brandCultureTitle || '品牌文化' }}</strong><span>⌂</span></div>
            <div v-if="isCulturePreview" class="mobile-preview-culture" :class="{ 'detail-first': displayForm.brandCultureDetailImages?.length }">
              <div v-if="displayForm.brandCultureDetailImages?.length" class="mobile-preview-culture-details"><div v-for="(image, index) in displayForm.brandCultureDetailImages" :key="`${image.url}-${index}`"><img :src="normalizeMediaUrl(image.url)" :alt="`详情图${index + 1}`" loading="lazy" @error="markCulturePreviewImageError" /><span>详情图 {{ index + 1 }} 加载失败</span></div></div>
              <template v-else>
                <img v-if="displayForm.brandCultureCoverUrl" :src="normalizeMediaUrl(displayForm.brandCultureCoverUrl)" alt="" />
                <span>{{ displayForm.brandCultureEnabled === 1 ? '页面已开启' : '页面已关闭' }}</span>
                <h3>{{ displayForm.brandCultureTitle || '品牌文化' }}</h3>
                <small>{{ displayForm.brandCultureSubtitle || '在这里介绍品牌理念与长期愿景' }}</small>
                <p>{{ safePreviewBrandCultureContent || '品牌内容正在准备中' }}</p>
              </template>
            </div>
            <div v-else-if="displayForm.layoutTemplate === 'category-focus' && previewPage === 'category'" class="mobile-category-guide-preview" :class="`guide-preview-${displayForm.categoryGuideTemplate || 'directory'}`">
              <div class="mobile-preview-search"><span>⌕</span><span>搜索商品</span><b>⌕</b></div>
              <template v-if="displayForm.categoryGuideTemplate === 'directory'">
                <p v-if="directoryGuidePreviewMode === 'empty'" class="preview-guide-invalid">请至少开启一个分类导购模块</p>
                <div v-else class="guide-preview-directory-body" :class="`is-${directoryGuidePreviewMode}`">
                  <aside v-if="directoryGuidePreviewMode === 'split'"><span v-for="category in visiblePreviewCategories.slice(0, 5)" :key="category.id">{{ category.categoryName }}</span></aside>
                  <div v-if="directoryGuidePreviewMode === 'primary-only'" class="guide-preview-primary-grid"><span v-for="category in visiblePreviewCategories.slice(0, 6)" :key="category.id">{{ category.categoryName }}</span></div>
                  <main v-if="displayForm.categoryGuideSubcategoriesEnabled === 1 || displayForm.categoryGuideHotProductsEnabled === 1"><section v-if="displayForm.categoryGuideSubcategoriesEnabled === 1"><b>精选子分类</b><div><span v-for="category in visiblePreviewCategories.slice(0, 4)" :key="category.id">{{ category.categoryName }}</span></div></section><section v-if="displayForm.categoryGuideHotProductsEnabled === 1"><b>热销好物</b><div class="preview-guide-products"><article v-for="product in previewProducts.slice(0, 4)" :key="product.id"><img v-if="product.coverUrl" :src="product.coverUrl" alt="" /><span>{{ product.productName }}</span><strong>¥{{ Number(product.salePrice || 0).toFixed(2) }}</strong></article></div></section></main>
                </div>
              </template>
              <template v-else-if="displayForm.categoryGuideTemplate === 'showcase'">
                <h3>全部品类</h3><div v-if="displayForm.categoryGuideHeroCategoriesEnabled === 1" class="preview-guide-showcase"><article v-for="(category, index) in visiblePreviewCategories.slice(0, 4)" :key="category.id"><img v-if="previewProducts[index]?.coverUrl" :src="previewProducts[index].coverUrl" alt="" /><strong>{{ category.categoryName }}</strong></article></div><div v-if="displayForm.categoryGuideShelvesEnabled === 1" class="preview-guide-tabs"><span>全部</span><span v-for="category in visiblePreviewCategories.slice(0, 3)" :key="category.id">{{ category.categoryName }}</span></div><div v-if="displayForm.categoryGuideRecommendedProductsEnabled === 1" class="preview-guide-products"><article v-for="product in previewProducts.slice(0, 4)" :key="product.id"><img v-if="product.coverUrl" :src="product.coverUrl" alt="" /><span>{{ product.productName }}</span><strong>¥{{ Number(product.salePrice || 0).toFixed(2) }}</strong></article></div>
              </template>
              <template v-else>
                <h3>今天想买什么？</h3><div v-if="displayForm.categoryGuideScenariosEnabled === 1" class="preview-guide-scenarios"><article v-for="(category, index) in visiblePreviewCategories.slice(0, 3)" :key="category.id"><img v-if="previewProducts[index]?.coverUrl" :src="previewProducts[index].coverUrl" alt="" /><span><strong>{{ category.categoryName }}</strong><small>按需求发现品质好物</small></span></article></div><section v-if="displayForm.categoryGuideQuickEntriesEnabled === 1"><b>也可以按品类找</b><div class="preview-guide-tabs"><span v-for="category in visiblePreviewCategories.slice(0, 4)" :key="category.id">{{ category.categoryName }}</span></div></section><section v-if="displayForm.categoryGuidePopularProductsEnabled === 1"><b>本周人气好物</b><div class="preview-guide-products"><article v-for="product in previewProducts.slice(0, 4)" :key="product.id"><img v-if="product.coverUrl" :src="product.coverUrl" alt="" /><span>{{ product.productName }}</span><strong>¥{{ Number(product.salePrice || 0).toFixed(2) }}</strong></article></div></section>
              </template>
              <p v-if="!visiblePreviewCategories.length && !previewProducts.length" class="preview-empty-module">关键配置缺失时前台显示安全兜底，不会白屏</p>
            </div>
            <template v-else-if="previewPage === 'home'">
              <div class="mobile-preview-search"><span>⌕</span><span>搜索商品</span><b>⌕</b></div>
              <template v-for="module in orderedPreviewModules" :key="module.type">
                <div v-if="module.type === 'banner' && module.enabled" class="mobile-preview-banner live-preview-banner">
                  <img v-if="previewBanners.length" :src="previewBanners[0].imageUrl" :alt="previewBanners[0].title || '商城活动'" />
                  <div v-else class="preview-empty-module"><strong>首页轮播图</strong><span>前往首页轮播图管理上传图片</span></div>
                  <i v-if="previewBanners.length > 1">● ○ ○</i>
                </div>
                <div v-else-if="module.type === 'notice' && module.enabled" class="mobile-preview-notice"><span>⌁</span><strong>商城公告</strong><small>欢迎来到{{ displayForm.brandName || '灵启商城' }}</small></div>
                <div v-else-if="module.type === 'category' && module.enabled && displayForm.showHomeCategories === 1" class="mobile-preview-categories live-preview-categories">
                  <div v-for="category in visiblePreviewCategories" :key="category.id" class="mobile-preview-category"><span><img v-if="category.iconUrl" :src="category.iconUrl" alt="" /><b v-else>{{ category.categoryName?.slice(0, 1) }}</b></span><strong>{{ category.categoryName }}</strong></div>
                  <div v-if="!visiblePreviewCategories.length" class="preview-empty-inline">暂无首页分类</div>
                </div>
                <div v-else-if="isPreviewFeatureAnchor(module)" class="mobile-preview-feature-row" :class="{ 'is-single': previewFeatureModules.length === 1 }">
                  <div v-if="showPreviewLive" class="mobile-preview-feature mobile-preview-live" :style="{ order: previewFeatureOrder('live') }">
                    <div><strong>直播广场</strong><small>全部 ›</small></div><section><b>直播间发布后展示</b><span>预告 · 直播中 · 回放</span></section>
                  </div>
                  <div v-if="showPreviewNewArrivals" class="mobile-preview-feature mobile-preview-new-arrivals" :style="{ order: previewFeatureOrder('newArrivals') }">
                    <div><strong>新品速递</strong><small>全部 ›</small></div><section><img v-if="previewProducts[0]?.coverUrl" :src="previewProducts[0].coverUrl" :alt="previewProducts[0].productName" /><b>{{ previewProducts[0]?.productName || '首次上架商品' }}</b><span>首发价 ¥{{ Number(previewProducts[0]?.salePrice || 0).toFixed(2) }}</span></section>
                  </div>
                </div>
                <div v-else-if="module.type === 'trust' && module.enabled && displayForm.showTrustStrip === 1" class="mobile-preview-trust"><span>安全支付</span><span>订单可查</span><span>售后无忧</span></div>
                <div v-else-if="module.type === 'products' && module.enabled" class="mobile-preview-product-section"><div class="mobile-preview-heading"><strong>精选商品</strong><span>商城好物，为你精选</span></div><div class="mobile-preview-products" :class="{ 'campaign-preview-products': displayForm.layoutTemplate === 'campaign-feed' }"><div v-for="product in previewProducts" :key="product.id" class="mobile-preview-product"><img v-if="product.coverUrl" :src="product.coverUrl" :alt="product.productName" /><i v-else></i><span v-if="displayForm.layoutTemplate === 'campaign-feed'" class="campaign-preview-band">活动好物 · 真实活动显示倒计时</span><strong>{{ product.productName }}</strong><small>{{ product.subtitle || '精选商品，品质保障' }}</small><b>¥{{ Number(product.salePrice || 0).toFixed(2) }}</b></div><div v-if="!previewProducts.length" class="preview-empty-module">暂无上架商品</div></div></div>
              </template>
            </template>
            <div v-if="!isCulturePreview" class="mobile-preview-nav" :style="{ gridTemplateColumns: `repeat(${Math.max(visiblePreviewNav.length, 1)}, minmax(0, 1fr))` }"><span v-for="nav in visiblePreviewNav" :key="nav.type" :class="{ active: nav.type === previewPage }" @click="openPreviewNav(nav.type)">{{ nav.label }}</span></div>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="closeDisplayDialog">取消</el-button>
        <el-button type="primary" :loading="savingDisplay" :disabled="savingDisplay || directoryGuideInvalid" @click="submitDisplayConfig">保存发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bannerDialogVisible" title="首页轮播图管理" width="1000px" top="3vh" append-to-body>
      <ShopBanners />
    </el-dialog>

    <el-dialog v-model="versionDialogVisible" title="商城配置版本记录" width="920px" append-to-body>
      <el-alert
        title="每次保存商城资料、视觉设置或启停状态都会自动生成版本；恢复后仍会保留当前配置，可继续回到恢复前。"
        type="info"
        :closable="false"
        show-icon
        class="version-alert"
      />
      <el-table :data="configVersions" v-loading="versionLoading" max-height="520">
        <el-table-column prop="versionNo" label="版本号" min-width="205" />
        <el-table-column label="变更内容" width="130">
          <template #default="{ row }">{{ configVersionTypeName(row.changeType) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作账号" width="130" />
        <el-table-column label="保存时间" width="175">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="100">
          <template #default="{ row }">
            <el-button type="primary" link :loading="restoringVersionId === row.id" @click="restoreVersion(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { formatDateTime } from '@/utils/dateTime'
import { resolveDirectoryGuideLayout } from '@/utils/categoryGuideLayout'
import { isEditableBottomNav, normalizeBottomNav } from '@/utils/bottomNav'
import { applyVisualLayoutTemplate } from '@/utils/layoutTemplate'
import {
  SHOP_THEME_OPTIONS,
  applyThemePresetToForm,
  hydrateThemeColors,
  isThemePresetActive,
  themePalette,
  themePreviewVariables,
} from '@/utils/shopTheme'
import { listShopBanners, listShopCategories, listShopProducts, updateCategoryShowOnHome, uploadBrandCultureImage, uploadShopImage } from '@/api/shop'
import ShopBanners from '@/views/shop/banners.vue'
import {
  getDisplayConfig,
  listTenantConfigVersions,
  listTenants,
  restoreTenantConfigVersion,
  saveDisplayConfig,
  saveTenant,
} from '@/api/tenant'

const loading = ref(false)
const route = useRoute()
const tableData = ref([])
const displayDialogVisible = ref(false)
const bannerDialogVisible = ref(false)
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const configVersions = ref([])
const versionTenant = ref(null)
const restoringVersionId = ref(null)
const currentTenant = ref(null)
const currentDisplayConfig = ref({ layoutTemplate: 'standard' })
const displayConfigLoaded = ref(false)
const categories = ref([])
const previewProducts = ref([])
const previewBanners = ref([])
const categoryDraft = ref({})
const previewPage = ref('home')
const activeEditSection = ref('layout')
const independentPageTab = ref('culture')
const draggingModuleIndex = ref(null)
const initializingDisplay = ref(false)
const displayDraftDirty = ref(false)
const savingDisplay = ref(false)
const displayLogoLoadFailed = ref(false)
const brandCultureDraggingIndex = ref(null)
const pendingCultureUploads = new Map()
const displayExtraBase = ref({})

const displayForm = ref({})
const looksLikeCultureImageReference = (value) => /^[^\r\n。！？；]{1,240}\.(?:jpe?g|png|webp|gif)(?:\?[^\r\n]*)?$/i.test(value)
const safePreviewBrandCultureContent = computed(() => {
  const value = String(displayForm.value.brandCultureContent || '').trim()
  return value && !looksLikeCultureImageReference(value) ? value : ''
})
const brandCultureContentWarning = computed(() => safePreviewBrandCultureContent.value
  ? '页面已开启，目前仍在展示旧文字介绍；建议上传详情图后再开放首页横幅入口。'
  : '页面已开启，但还没有详情图；前台会显示“品牌内容正在准备中”。建议素材上传完成后再开放首页横幅入口。')
const moduleNames = { banner: '首页轮播图', notice: '商城公告', category: '商品分类', live: '直播广场', newArrivals: '新品速递', trust: '服务保障', products: '精选商品' }
const navNames = { home: '首页', category: '分类', cart: '购物车', orders: '订单', profile: '我的' }
const workbenchGroups = [
  { key: 'layout', label: '整体版型', description: '先确定首页大框架' },
  { key: 'brand', label: '品牌与主题', description: '统一维护名称、Logo 和颜色' },
  { key: 'home', label: '首页模块', description: '配置内容、顺序和当前版型模块' },
  { key: 'pages', label: '独立页面', description: '管理直播、新品与品牌文化' },
  { key: 'nav', label: '底部导航', description: '只管理可编辑入口' },
]
const editSectionLabels = Object.fromEntries(workbenchGroups.map((item) => [item.key, item.label]))
const editSectionLabel = computed(() => editSectionLabels[activeEditSection.value] || '整体版型')
const activeWorkbenchGroup = computed(() => workbenchGroups.find((item) => item.key === activeEditSection.value))
const independentPages = [
  { key: 'culture', icon: '文', label: '品牌文化', description: '元数据与详情图' },
  { key: 'live', icon: '播', label: '直播广场', description: '页面总开关' },
  { key: 'newArrivals', icon: '新', label: '新品速递', description: '页面总开关与时间' },
]
const isCulturePreview = computed(() => activeEditSection.value === 'pages' && independentPageTab.value === 'culture')
const defaultModules = () => [
  { type: 'banner', enabled: true, sort: 1 },
  { type: 'notice', enabled: true, sort: 2 },
  { type: 'category', enabled: true, sort: 3 },
  { type: 'live', enabled: true, sort: 4 },
  { type: 'newArrivals', enabled: true, sort: 5 },
  { type: 'trust', enabled: false, sort: 6 },
  { type: 'products', enabled: true, sort: 7 },
]
const withDefaultModules = (configured) => {
  const raw = Array.isArray(configured) && configured.length ? configured : []
  const legacyDiscovery = raw.find((item) => item?.type === 'discovery')
  const current = raw.filter((item) => item?.type !== 'discovery')
  if (legacyDiscovery) {
    const legacySort = Number(legacyDiscovery.sort) || 4
    if (!current.some((item) => item?.type === 'live')) current.push({ ...legacyDiscovery, type: 'live', sort: legacySort })
    if (!current.some((item) => item?.type === 'newArrivals')) current.push({ ...legacyDiscovery, type: 'newArrivals', sort: legacySort + 0.1 })
  }
  const types = new Set(current.map((item) => item?.type).filter(Boolean))
  const rank = new Map(defaultModules().map((item, index) => [item.type, index]))
  return [...current, ...defaultModules().filter((item) => !types.has(item.type))]
    .sort((a, b) => (Number(a.sort || 0) - Number(b.sort || 0))
      || ((rank.get(a.type) ?? 99) - (rank.get(b.type) ?? 99)))
    .map((item, index) => ({ ...item, sort: index + 1 }))
}
const requiredNavTypes = new Set(['home', 'cart', 'profile'])
const isRequiredNav = (type) => requiredNavTypes.has(type)
const categoryGuideTemplateOptions = [
  { value: 'directory', label: 'A 双栏目录导航', description: '左侧一级分类，右侧子分类与热销商品，适合分类较多的商城' },
  { value: 'showcase', label: 'B 视觉品类橱窗', description: '大图品类卡、横向货架与推荐商品，适合强调视觉陈列' },
  { value: 'scenario', label: 'C 需求场景导购', description: '购物场景、快捷品类与人气商品，适合按需求启发选购' },
]
const selectedCategoryGuideLabel = computed(() => categoryGuideTemplateOptions.find((item) => item.value === displayForm.value.categoryGuideTemplate)?.label || 'A 双栏目录导航')
const categoryGuideModuleGroups = [
  { template: 'directory', modules: [['categoryGuidePrimaryCategoriesEnabled', '一级分类'], ['categoryGuideSubcategoriesEnabled', '子分类'], ['categoryGuideHotProductsEnabled', '热销商品']] },
  { template: 'showcase', modules: [['categoryGuideHeroCategoriesEnabled', '大型视觉品类'], ['categoryGuideShelvesEnabled', '品类货架'], ['categoryGuideRecommendedProductsEnabled', '推荐商品']] },
  { template: 'scenario', modules: [['categoryGuideScenariosEnabled', '购物场景'], ['categoryGuideQuickEntriesEnabled', '分类快捷入口'], ['categoryGuidePopularProductsEnabled', '人气商品']] },
]
const selectedCategoryGuideModules = computed(() => categoryGuideModuleGroups.find((group) => group.template === displayForm.value.categoryGuideTemplate)?.modules || [])
const directoryGuidePreviewMode = computed(() => resolveDirectoryGuideLayout({
  primaryCategories: displayForm.value.categoryGuidePrimaryCategoriesEnabled,
  subcategories: displayForm.value.categoryGuideSubcategoriesEnabled,
  hotProducts: displayForm.value.categoryGuideHotProductsEnabled,
}))
const directoryGuideInvalid = computed(() => displayForm.value.layoutTemplate === 'category-focus'
  && displayForm.value.categoryGuideTemplate === 'directory'
  && directoryGuidePreviewMode.value === 'empty')
const colorFields = [
  { key: 'priceColor', label: '价格色' },
  { key: 'pageBg', label: '页面背景' },
  { key: 'headerBg', label: '顶部背景' },
  { key: 'cardBg', label: '卡片背景' },
  { key: 'textColor', label: '主文字色' },
  { key: 'mutedColor', label: '辅助文字色' },
  { key: 'accentColor', label: '强调色' },
  { key: 'lineColor', label: '分割线色' },
  { key: 'buttonBg', label: '按钮背景' },
]
const themeOptions = SHOP_THEME_OPTIONS
const layoutTemplateOptions = [
  {
    value: 'standard',
    label: '标准零售版',
    description: '首页信息均衡展示，适合综合商城',
  },
  {
    value: 'product-focus',
    label: '紧凑商品版',
    description: '压缩卡片留白、提高商品密度，适合快速浏览商品',
  },
  {
    value: 'category-focus',
    label: '分类导购版',
    description: '放大分类视觉，三种分类页导购结构可选',
  },
  {
    value: 'campaign-feed',
    label: '活动信息流版',
    description: '大图信息流商品卡，真实秒杀显示倒计时，适合活动运营',
  },
]
const currentLayoutSummary = computed(() => {
  const layout = layoutTemplateOptions.find((item) => item.value === currentDisplayConfig.value?.layoutTemplate)?.label || '标准零售版'
  if (currentDisplayConfig.value?.layoutTemplate !== 'category-focus') return layout
  const guide = categoryGuideTemplateOptions.find((item) => item.value === currentDisplayConfig.value?.categoryGuideTemplate)?.label || 'A 双栏目录导航'
  return `${layout} · ${guide}`
})
const legacyThemeMap = { standard: 'retail-red', beauty: 'soft-purple', food: 'fresh-green', health: 'fresh-green', course: 'premium-gold' }
const normalizeTheme = (value) => themeOptions.some((item) => item.value === value) ? value : (legacyThemeMap[value] || 'retail-red')
const normalizeModuleEnabled = (value, fallback = true) => {
  if ([false, 0, '0', 'false'].includes(value)) return false
  if ([true, 1, '1', 'true'].includes(value)) return true
  return fallback
}
const normalizeMediaUrl = (value) => {
  const url = String(value || '').trim()
  if (!url) return ''
  if (/^(?:https?:|data:|blob:)/i.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}
const markCulturePreviewImageError = (event) => event.currentTarget?.classList.add('is-error')

const fetchData = async () => {
  loading.value = true
  displayConfigLoaded.value = false
  try {
    const res = await listTenants({ pageNum: 1, pageSize: 100 })
    const rows = res.data?.list || []
    const current = rows.find((row) => Number(row.id) === 1) || rows[0]
    tableData.value = current ? [current] : []
    if (current) {
      const configRes = await getDisplayConfig(current.id)
      currentDisplayConfig.value = configRes.data || { layoutTemplate: 'standard' }
      displayConfigLoaded.value = Boolean(configRes.data)
    }
  } finally {
    loading.value = false
  }
}

const uploadDisplayLogo = async ({ file }) => {
  const res = await uploadShopImage(file)
  displayForm.value.logoUrl = normalizeMediaUrl(res.data)
  displayLogoLoadFailed.value = false
  ElMessage.success('品牌LOGO上传成功')
}

const allowedCultureImage = (file) => {
  const extension = String(file.name || '').toLowerCase().split('.').pop()
  return ['jpg', 'jpeg', 'png', 'webp'].includes(extension)
    && ['image/jpeg', 'image/png', 'image/webp'].includes(String(file.type || '').toLowerCase())
}
const cultureUploadKey = (file) => `${file.uid || ''}:${file.name}:${file.size}:${file.lastModified || ''}`
const cultureDetailBytes = () => (displayForm.value.brandCultureDetailImages || [])
  .reduce((total, image) => total + Number(image.size || 0), 0)
const beforeBrandCultureDetailUpload = (file) => {
  if (!allowedCultureImage(file)) {
    ElMessage.error(`${file.name} 不是可用的JPG、PNG或WebP图片，请重新导出后上传`)
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error(`${file.name} 超过5MB，请压缩后再上传`)
    return false
  }
  if ((displayForm.value.brandCultureDetailImages?.length || 0) + pendingCultureUploads.size >= 10) {
    ElMessage.error(`${file.name} 无法上传：详情图最多10张`)
    return false
  }
  const pendingBytes = [...pendingCultureUploads.values()].reduce((sum, value) => sum + value, 0)
  if (cultureDetailBytes() + pendingBytes + file.size > 30 * 1024 * 1024) {
    const over = cultureDetailBytes() + pendingBytes + file.size - 30 * 1024 * 1024
    ElMessage.error(`${file.name} 上传后合计将超出30MB约${formatFileSize(over)}，请先删除或压缩图片`)
    return false
  }
  pendingCultureUploads.set(cultureUploadKey(file), file.size)
  return true
}
const uploadBrandCultureDetail = async ({ file }) => {
  try {
    const res = await uploadBrandCultureImage(displayForm.value.tenantId, 'detail', file)
    displayForm.value.brandCultureDetailImages = [
      ...(displayForm.value.brandCultureDetailImages || []),
      { url: normalizeMediaUrl(res.data?.url), size: Number(res.data?.size || file.size) },
    ]
    ElMessage.success(`${file.name} 上传成功`)
  } catch (error) {
    ElMessage.error(`${file.name} 上传失败：${error?.message || '请检查图片后重试'}`)
  } finally {
    pendingCultureUploads.delete(cultureUploadKey(file))
  }
}
const formatFileSize = (size) => {
  const bytes = Number(size || 0)
  if (!bytes) return '大小将在保存时核验'
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(2)}MB` : `${Math.ceil(bytes / 1024)}KB`
}
const removeBrandCultureDetail = (index) => displayForm.value.brandCultureDetailImages.splice(index, 1)
const clearBrandCultureDetails = async () => {
  try {
    await ElMessageBox.confirm('清空后，前台将停止展示全部详情图。旧文字内容仍会保留并作为兜底。', '确认清空全部详情图？', {
      confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning',
    })
    displayForm.value.brandCultureDetailImages = []
  } catch { /* 用户取消 */ }
}
const dropBrandCultureImage = (index) => {
  const from = brandCultureDraggingIndex.value
  if (from == null || from === index) return
  reorderItems(displayForm.value.brandCultureDetailImages, from, index)
  brandCultureDraggingIndex.value = null
}

const normalizeWorkbenchSection = (section) => ({
  culture: 'pages', live: 'pages', newArrivals: 'pages', banner: 'home', category: 'home', colors: 'brand',
}[section] || (Object.prototype.hasOwnProperty.call(editSectionLabels, section) ? section : 'layout'))

const openDisplayDialog = async (row, section = 'layout') => {
  initializingDisplay.value = true
  displayDraftDirty.value = false
  previewPage.value = 'home'
  activeEditSection.value = normalizeWorkbenchSection(section)
  if (['culture', 'live', 'newArrivals'].includes(section)) independentPageTab.value = section
  currentTenant.value = row
  displayLogoLoadFailed.value = false
  const [resResult, categoryResult, productResult, bannerResult] = await Promise.allSettled([
    getDisplayConfig(row.id),
    listShopCategories({ tenantId: row.id, status: 1 }),
    listShopProducts({ tenantId: row.id, status: 1, pageNum: 1, pageSize: 6 }),
    listShopBanners({ tenantId: row.id }),
  ])
  if (resResult.status === 'rejected') throw resResult.reason
  const res = resResult.value
  const categoryRes = categoryResult.status === 'fulfilled' ? categoryResult.value : { data: [] }
  const productRes = productResult.status === 'fulfilled' ? productResult.value : { data: [] }
  const bannerRes = bannerResult.status === 'fulfilled' ? bannerResult.value : { data: [] }
  categories.value = Array.isArray(categoryRes.data) ? categoryRes.data : (categoryRes.data?.list || [])
  previewProducts.value = Array.isArray(productRes.data) ? productRes.data : (productRes.data?.list || [])
  previewBanners.value = (Array.isArray(bannerRes.data) ? bannerRes.data : (bannerRes.data?.list || [])).filter((banner) => Number(banner.status ?? 1) === 1)
  categoryDraft.value = Object.fromEntries(categories.value.map((category) => [category.id, Number(category.showOnHome ?? 1)]))
  const raw = res.data?.extraConfigJson || '{}'
  let extra = {}
  try { extra = JSON.parse(raw) || {} } catch { extra = {} }
  displayExtraBase.value = extra && typeof extra === 'object' && !Array.isArray(extra) ? extra : {}
  const legacyBottomCategoryEnabled = Number(res.data?.showBottomCategoryNav ?? 1) === 1
  const hasConfiguredBottomNav = Array.isArray(extra.bottomNav) && extra.bottomNav.length > 0
  const legacyTemplateCoupledCategory = !Object.prototype.hasOwnProperty.call(extra, 'bottomNavIndependent')
    && res.data?.layoutTemplate === 'product-focus'
    && Number(res.data?.showBottomCategoryNav ?? 1) === 0
  const bottomNav = normalizeBottomNav(hasConfiguredBottomNav ? extra.bottomNav : null, {
    legacyCategoryEnabled: hasConfiguredBottomNav ? true : legacyBottomCategoryEnabled,
  })
  // 旧版“紧凑商品版”会强制关闭分类底栏；首次进入新工作台时仅修复这一可识别的历史副作用。
  if (legacyTemplateCoupledCategory) bottomNav.find((nav) => nav.type === 'category').enabled = true
  const configuredModules = withDefaultModules(extra.homeModules)
  const trustEnabled = normalizeModuleEnabled(
    extra.showTrustStrip ?? configuredModules.find((module) => module.type === 'trust')?.enabled,
    false,
  )
  const homeModules = configuredModules.map((module) => ({
    ...module,
    enabled: module.type === 'trust' ? trustEnabled : normalizeModuleEnabled(module.enabled),
  }))
  const productTemplate = normalizeTheme(row.productTemplate)
  const selectedTheme = themeOptions.find((theme) => theme.value === productTemplate) || themeOptions[0]
  const themeColor = row.themeColor || selectedTheme.color
  displayForm.value = {
    tenantId: row.id,
    brandName: row.brandName || row.tenantName || '灵启商城',
    logoUrl: normalizeMediaUrl(row.logoUrl),
    themeColor,
    productTemplate,
    brandCultureEnabled: Number(row.brandCultureEnabled ?? 0) === 1 ? 1 : 0,
    brandCultureTitle: row.brandCultureTitle || '',
    brandCultureSubtitle: row.brandCultureSubtitle || '',
    brandCultureCoverUrl: normalizeMediaUrl(row.brandCultureCoverUrl),
    brandCultureContent: row.brandCultureContent || '',
    brandCultureDetailImages: (res.data?.brandCultureDetailImages || extra.brandCultureDetailImages || [])
      .map((image) => typeof image === 'string' ? { url: normalizeMediaUrl(image), size: 0 } : { url: normalizeMediaUrl(image?.url), size: Number(image?.size || 0) })
      .filter((image) => image.url).slice(0, 10),
    layoutTemplate: 'standard',
    showHomeCategories: 1,
    showBottomCategoryNav: 1,
    ...(res.data || {}),
    showHomeCategories: Number(res.data?.showHomeCategories ?? 1) === 0 ? 0 : 1,
    homeModules,
    colors: hydrateThemeColors(selectedTheme, themeColor, extra.colors),
    bottomNav,
    showTrustStrip: trustEnabled ? 1 : 0,
    liveSquareEnabled: Number(res.data?.liveSquareEnabled ?? extra.liveSquareEnabled ?? 1) === 0 ? 0 : 1,
    newArrivalsEnabled: Number(res.data?.newArrivalsEnabled ?? extra.newArrivalsEnabled ?? 1) === 0 ? 0 : 1,
    newArrivalWindowDays: Number(res.data?.newArrivalWindowDays ?? extra.newArrivalWindowDays ?? 30),
    categoryGuideTemplate: res.data?.categoryGuideTemplate || extra.categoryGuideTemplate || 'directory',
    categoryGuidePrimaryCategoriesEnabled: Number(res.data?.categoryGuidePrimaryCategoriesEnabled ?? extra.categoryGuideModules?.primaryCategories ?? 1) === 0 ? 0 : 1,
    categoryGuideSubcategoriesEnabled: Number(res.data?.categoryGuideSubcategoriesEnabled ?? extra.categoryGuideModules?.subcategories ?? 1) === 0 ? 0 : 1,
    categoryGuideHotProductsEnabled: Number(res.data?.categoryGuideHotProductsEnabled ?? extra.categoryGuideModules?.hotProducts ?? 1) === 0 ? 0 : 1,
    categoryGuideHeroCategoriesEnabled: Number(res.data?.categoryGuideHeroCategoriesEnabled ?? extra.categoryGuideModules?.heroCategories ?? 1) === 0 ? 0 : 1,
    categoryGuideShelvesEnabled: Number(res.data?.categoryGuideShelvesEnabled ?? extra.categoryGuideModules?.shelves ?? 1) === 0 ? 0 : 1,
    categoryGuideRecommendedProductsEnabled: Number(res.data?.categoryGuideRecommendedProductsEnabled ?? extra.categoryGuideModules?.recommendedProducts ?? 1) === 0 ? 0 : 1,
    categoryGuideScenariosEnabled: Number(res.data?.categoryGuideScenariosEnabled ?? extra.categoryGuideModules?.scenarios ?? 1) === 0 ? 0 : 1,
    categoryGuideQuickEntriesEnabled: Number(res.data?.categoryGuideQuickEntriesEnabled ?? extra.categoryGuideModules?.quickEntries ?? 1) === 0 ? 0 : 1,
    categoryGuidePopularProductsEnabled: Number(res.data?.categoryGuidePopularProductsEnabled ?? extra.categoryGuideModules?.popularProducts ?? 1) === 0 ? 0 : 1,
  }
  displayDialogVisible.value = true
  await nextTick()
  initializingDisplay.value = false
  displayDraftDirty.value = false
}

const configVersionTypeName = (value) => ({
  INITIAL: '初始配置',
  BASELINE: '历史基线',
  PROFILE_UPDATE: '商城资料',
  DISPLAY_UPDATE: '视觉与页面',
  STATUS_UPDATE: '启停状态',
  PRE_RESTORE: '恢复前备份',
  RESTORE: '恢复版本',
}[value] || '配置更新')

const loadConfigVersions = async () => {
  if (!versionTenant.value?.id) return
  versionLoading.value = true
  try {
    const res = await listTenantConfigVersions(versionTenant.value.id)
    configVersions.value = Array.isArray(res.data) ? res.data : []
  } finally {
    versionLoading.value = false
  }
}

const openVersionDialog = async (row) => {
  versionTenant.value = row
  versionDialogVisible.value = true
  await loadConfigVersions()
}

const restoreVersion = async (row) => {
  await ElMessageBox.confirm(
    `确定恢复到 ${row.versionNo} 吗？恢复前的当前配置会自动保存为历史版本。`,
    '确认恢复商城配置',
    { confirmButtonText: '确认恢复', cancelButtonText: '取消', type: 'warning' },
  )
  restoringVersionId.value = row.id
  try {
    await restoreTenantConfigVersion(versionTenant.value.id, row.id)
    ElMessage.success('商城配置已恢复，请刷新客户前台查看')
    await Promise.all([fetchData(), loadConfigVersions()])
  } finally {
    restoringVersionId.value = null
  }
}

const applyDisplayTheme = (theme) => {
  applyThemePresetToForm(displayForm.value, theme)
}

const setTrustEnabled = (value) => {
  const enabled = value === true || Number(value) === 1
  displayForm.value.showTrustStrip = enabled ? 1 : 0
  const trustModule = (displayForm.value.homeModules || []).find((module) => module.type === 'trust')
  if (trustModule) trustModule.enabled = enabled
}

const resetColors = () => {
  const theme = themeOptions.find((item) => item.value === normalizeTheme(displayForm.value.productTemplate)) || themeOptions[0]
  displayForm.value.colors = themePalette(theme, displayForm.value.themeColor)
  ElMessage.success('颜色已恢复默认，点击“保存发布”后客户前台生效')
}

const orderedPreviewModules = computed(() => [...(displayForm.value.homeModules || [])].sort((a, b) => (a.sort || 99) - (b.sort || 99)))
const previewFeatureModules = computed(() => orderedPreviewModules.value.filter((module) => {
  if (!module.enabled) return false
  if (module.type === 'live') return displayForm.value.liveSquareEnabled === 1
  if (module.type === 'newArrivals') return displayForm.value.newArrivalsEnabled === 1
  return false
}))
const showPreviewLive = computed(() => previewFeatureModules.value.some((module) => module.type === 'live'))
const showPreviewNewArrivals = computed(() => previewFeatureModules.value.some((module) => module.type === 'newArrivals'))
const isPreviewFeatureAnchor = (module) => module?.type === previewFeatureModules.value[0]?.type
const previewFeatureOrder = (type) => previewFeatureModules.value.findIndex((module) => module.type === type) + 1
const visiblePreviewCategories = computed(() => categories.value.filter((category) => Number(categoryDraft.value[category.id] ?? 1) === 1))
const visiblePreviewNav = computed(() => (displayForm.value.bottomNav || []).filter((nav) => nav.enabled !== false))
const configurableBottomNav = computed(() => (displayForm.value.bottomNav || [])
  .filter((nav) => isEditableBottomNav(nav.type)))
const previewStyle = computed(() => themePreviewVariables(displayForm.value, currentTenant.value?.themeColor || '#e7193f'))

const applyLayoutTemplate = (template) => {
  applyVisualLayoutTemplate(displayForm.value, template?.value)
  previewPage.value = 'home'
}

const openPreviewNav = (type) => {
  if (type === 'home' || (type === 'category' && displayForm.value.layoutTemplate === 'category-focus')) {
    previewPage.value = type
  }
}

const moveModule = (index, direction) => {
  const next = index + direction
  if (next < 0 || next >= displayForm.value.homeModules.length) return
  const modules = displayForm.value.homeModules
  ;[modules[index], modules[next]] = [modules[next], modules[index]]
  modules.forEach((module, itemIndex) => { module.sort = itemIndex + 1 })
}

const reorderItems = (items, from, to) => {
  if (from === null || from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return
  const [item] = items.splice(from, 1)
  items.splice(to, 0, item)
}

const startModuleDrag = (index) => { draggingModuleIndex.value = index }
const dropModule = (index) => {
  const modules = displayForm.value.homeModules || []
  reorderItems(modules, draggingModuleIndex.value, index)
  modules.forEach((module, itemIndex) => { module.sort = itemIndex + 1 })
  draggingModuleIndex.value = null
}
const setCategoryDraft = (category, value) => {
  categoryDraft.value = { ...categoryDraft.value, [category.id]: Number(value) }
}

watch(displayForm, () => {
  if (!initializingDisplay.value && displayDialogVisible.value) displayDraftDirty.value = true
}, { deep: true })
watch(categoryDraft, () => {
  if (!initializingDisplay.value && displayDialogVisible.value) displayDraftDirty.value = true
}, { deep: true })

const closeDisplayDialog = () => {
  if (!displayDraftDirty.value) {
    displayDialogVisible.value = false
    return
  }
  ElMessageBox.confirm('当前装修草稿尚未发布，关闭后这些修改会丢失。', '确认放弃未保存修改？', {
    confirmButtonText: '放弃修改',
    cancelButtonText: '继续编辑',
    type: 'warning',
  }).then(() => {
    displayDialogVisible.value = false
    displayDraftDirty.value = false
  }).catch(() => {})
}

const confirmCloseDisplayDialog = (done) => {
  if (!displayDraftDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('当前装修草稿尚未发布，关闭后这些修改会丢失。', '确认放弃未保存修改？', {
    confirmButtonText: '放弃修改',
    cancelButtonText: '继续编辑',
    type: 'warning',
  }).then(() => {
    displayDraftDirty.value = false
    done()
  }).catch(() => {})
}

const submitDisplayConfig = async () => {
  if (savingDisplay.value) return
  const tenantId = currentTenant.value?.id || displayForm.value?.tenantId
  if (!tenantId) {
    ElMessage.error('未找到商城配置，请关闭后重新打开装修工作台')
    return
  }
  const windowDays = Number(displayForm.value.newArrivalWindowDays)
  if (windowDays !== 0 && (!Number.isInteger(windowDays) || windowDays < 30 || windowDays > 365)) {
    ElMessage.warning('自动新品展示时间必须是30到365天之间的整数，或选择永久')
    return
  }
  if (directoryGuideInvalid.value) {
    ElMessage.warning('请至少开启一个分类导购模块')
    return
  }

  savingDisplay.value = true
  try {
    const form = displayForm.value || {}
    const bottomNav = normalizeBottomNav(form.bottomNav).map((nav) => {
      const { systemRequired: _legacySystemRequired, ...cleanNav } = nav
      return isRequiredNav(nav.type) ? { ...cleanNav, enabled: true } : cleanNav
    })
    const categoryNav = bottomNav.find((nav) => nav.type === 'category')
    const showBottomCategoryNav = categoryNav?.enabled === false ? 0 : 1
    // 只提交后端实体字段；homeModules/colors/bottomNav/showTrustStrip 等编辑态字段统一放进扩展 JSON，避免 Jackson 因未知字段拒绝请求。
    const payload = {
      id: form.id,
      tenantId,
      showPv: form.showPv,
      showTeamPerformance: form.showTeamPerformance,
      showBonusSource: form.showBonusSource,
      showBonusFlow: form.showBonusFlow,
      showProfit: form.showProfit,
      showRank: form.showRank,
      showBinaryArea: form.showBinaryArea,
      showRetailModule: form.showRetailModule,
      showStoreModule: form.showStoreModule,
      showCompanyShare: form.showCompanyShare,
      layoutTemplate: form.layoutTemplate,
      showHomeCategories: form.showHomeCategories,
      showBottomCategoryNav,
      liveSquareEnabled: form.liveSquareEnabled,
      newArrivalsEnabled: form.newArrivalsEnabled,
      newArrivalWindowDays: form.newArrivalWindowDays,
      categoryGuideTemplate: form.categoryGuideTemplate,
      categoryGuidePrimaryCategoriesEnabled: form.categoryGuidePrimaryCategoriesEnabled,
      categoryGuideSubcategoriesEnabled: form.categoryGuideSubcategoriesEnabled,
      categoryGuideHotProductsEnabled: form.categoryGuideHotProductsEnabled,
      categoryGuideHeroCategoriesEnabled: form.categoryGuideHeroCategoriesEnabled,
      categoryGuideShelvesEnabled: form.categoryGuideShelvesEnabled,
      categoryGuideRecommendedProductsEnabled: form.categoryGuideRecommendedProductsEnabled,
      categoryGuideScenariosEnabled: form.categoryGuideScenariosEnabled,
      categoryGuideQuickEntriesEnabled: form.categoryGuideQuickEntriesEnabled,
      categoryGuidePopularProductsEnabled: form.categoryGuidePopularProductsEnabled,
      brandCultureDetailImages: form.brandCultureDetailImages || [],
      productDetailEnabled: 1,
      cartEnabled: 1,
      checkoutEnabled: 1,
      accountSecurityEnabled: 1,
      legalComplianceEnabled: 1,
      afterSalesEnabled: 1,
      customerServiceEnabled: 1,
      extraConfigJson: JSON.stringify({
        ...displayExtraBase.value,
        homeModules: form.homeModules,
        colors: form.colors,
        bottomNav,
        bottomNavIndependent: 1,
        showTrustStrip: form.showTrustStrip,
        liveSquareEnabled: form.liveSquareEnabled,
        newArrivalsEnabled: form.newArrivalsEnabled,
        newArrivalWindowDays: form.newArrivalWindowDays,
        categoryGuideTemplate: form.categoryGuideTemplate,
        categoryGuideModules: {
          primaryCategories: form.categoryGuidePrimaryCategoriesEnabled,
          subcategories: form.categoryGuideSubcategoriesEnabled,
          hotProducts: form.categoryGuideHotProductsEnabled,
          heroCategories: form.categoryGuideHeroCategoriesEnabled,
          shelves: form.categoryGuideShelvesEnabled,
          recommendedProducts: form.categoryGuideRecommendedProductsEnabled,
          scenarios: form.categoryGuideScenariosEnabled,
          quickEntries: form.categoryGuideQuickEntriesEnabled,
          popularProducts: form.categoryGuidePopularProductsEnabled,
        },
        brandCultureDetailImages: form.brandCultureDetailImages || [],
        requiredCapabilities: {
          productDetail: 1, cart: 1, checkout: 1, accountSecurity: 1,
          legalCompliance: 1, afterSales: 1, customerService: 1,
        },
      }),
    }
    const tenantPayload = {
      ...currentTenant.value,
      id: tenantId,
      brandName: form.brandName,
      logoUrl: form.logoUrl,
      themeColor: form.themeColor,
      productTemplate: normalizeTheme(form.productTemplate),
      brandCultureEnabled: form.brandCultureEnabled,
      brandCultureTitle: form.brandCultureTitle?.trim() || null,
      brandCultureSubtitle: form.brandCultureSubtitle?.trim() || null,
      brandCultureCoverUrl: form.brandCultureCoverUrl || null,
      brandCultureContent: form.brandCultureContent?.trim() || null,
    }
    // 资料与视觉配置按顺序保存，确保每个历史版本都是完整一致的商城快照。
    const tenantResult = await saveTenant(tenantPayload, { silentError: true })
    const displayResult = await saveDisplayConfig(payload, { silentError: true })
    if (tenantResult.data) {
      currentTenant.value = { ...currentTenant.value, ...tenantResult.data }
      tableData.value = tableData.value.map((row) => Number(row.id) === Number(tenantId) ? { ...row, ...tenantResult.data } : row)
    }
    const categoryUpdates = categories.value
      .filter((category) => Number(categoryDraft.value[category.id] ?? 1) !== Number(category.showOnHome ?? 1))
      .map((category) => updateCategoryShowOnHome(category.id, categoryDraft.value[category.id]))
    const categoryResults = await Promise.allSettled(categoryUpdates)
    categories.value.forEach((category) => { category.showOnHome = categoryDraft.value[category.id] ?? category.showOnHome })
    if (displayResult.data) currentDisplayConfig.value = displayResult.data
    if (categoryResults.some((result) => result.status === 'rejected')) {
      ElMessage.warning('页面配置已保存，但部分分类显示状态保存失败，请重试')
    } else {
      ElMessage.success('商城首页装修已发布，网页和 APP 刷新后生效')
    }
    displayDraftDirty.value = false
    displayDialogVisible.value = false
  } catch (error) {
    console.error('商城视觉装修发布失败:', error)
    ElMessage.error(error?.message || '商城视觉装修发布失败，请检查网络后重试')
  } finally {
    savingDisplay.value = false
  }
}

const getLayoutTemplateName = (value) => {
  return layoutTemplateOptions.find((item) => item.value === value)?.label || '标准零售版'
}

const getTemplateName = (value) => {
  const map = {
    'retail-red': '热卖红',
    'fresh-green': '清新绿',
    'premium-gold': '轻奢金',
    'soft-purple': '雅致紫',
  }
  return map[normalizeTheme(value)] || '热卖红'
}

onMounted(async () => {
  await fetchData()
  const editSection = String(route.query.editSection || '')
  if (tableData.value[0] && editSection) {
    await openDisplayDialog(tableData.value[0], editSection)
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}
.toolbar h2 {
  margin: 0;
  color: #303133;
  font-size: 20px;
}
.toolbar p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-alert {
  margin-bottom: 16px;
}
.current-decoration-card { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:24px; min-height:400px; padding:32px 36px; overflow:hidden; background:linear-gradient(135deg,#fff 0%,#f8fbff 72%,#eef5ff 100%); border:1px solid #e1e8f0; border-radius:20px; box-shadow:0 16px 38px rgba(31,55,85,.09); }
.current-decoration-main { display:flex; flex-direction:column; justify-content:center; min-width:0; }
.current-decoration-kicker { margin-bottom:18px; color:#1556a3; font-size:14px; font-weight:700; letter-spacing:.08em; }
.current-decoration-brand { display:flex; align-items:center; gap:16px; }
.current-decoration-logo { display:grid; flex:0 0 64px; width:64px; height:64px; place-items:center; overflow:hidden; color:#1556a3; background:#fff; border:1px solid #dce6f1; border-radius:16px; box-shadow:0 8px 18px rgba(32,68,108,.08); }
.current-decoration-logo img { width:100%; height:100%; object-fit:contain; }
.current-decoration-logo b { font-size:26px; }
.current-decoration-brand h3 { margin:0 0 8px; color:#1b2430; font-size:26px; line-height:1.25; }
.current-decoration-theme { display:flex; align-items:center; gap:7px; color:#6b7280; font-size:13px; }
.current-decoration-theme i { width:16px; height:16px; border:2px solid #fff; border-radius:50%; box-shadow:0 0 0 1px #d8dee8; }
.current-decoration-layout { display:flex; align-items:center; gap:10px; margin-top:28px; padding:15px 0; border-top:1px solid #e8ecf1; border-bottom:1px solid #e8ecf1; }
.current-decoration-layout > span { color:#6b7280; font-size:13px; }
.current-decoration-layout strong { color:#1b2430; font-size:16px; }
.current-decoration-layout .el-tag { margin-left:auto; }
.current-decoration-main > p { margin:18px 0 22px; color:#6b7280; font-size:14px; line-height:1.75; }
.current-decoration-actions { display:flex; flex-wrap:wrap; gap:10px; }
.current-decoration-preview { display:grid; align-items:center; justify-items:start; min-width:0; }
.decoration-phone { display:flex; box-sizing:border-box; width:190px; height:320px; flex-direction:column; overflow:hidden; padding:9px; color:#1b2430; background:#f6f7f9; border:7px solid #1b2430; border-radius:28px; box-shadow:0 18px 32px rgba(27,36,48,.18); }
.decoration-phone-status { padding:0 5px 7px; font-size:9px; font-weight:700; }
.decoration-phone-brand { display:flex; align-items:center; gap:7px; padding:8px; background:#fff; border-radius:10px; }
.decoration-phone-brand i { width:18px; height:18px; border-radius:6px; }
.decoration-phone-brand strong { overflow:hidden; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.decoration-phone-search { margin:8px 0; padding:7px 10px; color:#9aa3af; font-size:9px; background:#fff; border:1px solid #dce4ed; border-radius:999px; }
.decoration-phone .current-layout-preview { width:100%; height:auto; min-height:186px; flex:1; box-sizing:border-box; }
.decoration-phone-nav { display:grid; grid-template-columns:repeat(4,1fr); gap:2px; padding:8px 1px 2px; color:#7b8491; font-size:8px; text-align:center; }
.ui-preview {
  width: 100%;
  max-width: 720px;
  overflow: hidden;
  color: #253044;
  background: #f6f7f9;
  border: 1px solid #e4e7ed;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(31, 45, 61, .08);
}
.ui-preview-head {
  display: grid;
  grid-template-columns: 28px auto minmax(120px, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 14px 16px;
  color: #fff;
  background: linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 88%, #111 12%), var(--preview-color));
}
.ui-preview-logo {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: var(--preview-color);
  font-weight: 700;
  background: #fff;
  border-radius: 9px;
}
.ui-preview-head strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ui-preview-search { padding: 6px 10px; color: rgba(255,255,255,.75); font-size: 12px; background: rgba(255,255,255,.16); border-radius: 999px; }
.ui-preview-avatar { font-size: 12px; opacity: .86; }
.ui-preview-categories { display: flex; gap: 8px; padding: 12px 16px 4px; overflow: hidden; white-space: nowrap; }
.ui-preview-categories span { padding: 5px 10px; color: var(--preview-color); font-size: 12px; background: color-mix(in srgb, var(--preview-color) 12%, #fff 88%); border-radius: 999px; }
.ui-preview-products { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; padding: 10px 16px 16px; }
.ui-preview-product { display: grid; gap: 6px; min-width: 0; padding: 10px; background: #fff; border-radius: 10px; }
.ui-preview-product i { display: block; height: 62px; background: linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 18%, #fff 82%), #eef1f4); border-radius: 8px; }
.ui-preview-product strong,.ui-preview-product small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ui-preview-product strong { font-size: 13px; }
.ui-preview-product small { color: #8a94a4; font-size: 11px; }
.ui-preview-product b { color: var(--preview-color); font-size: 14px; }
.ui-preview-nav { display: grid; grid-template-columns: repeat(4, 1fr); padding: 10px 16px; color: #8a94a4; font-size: 11px; text-align: center; background: #fff; border-top: 1px solid #eef0f3; }
.ui-preview-nav .active { color: var(--preview-color); font-weight: 700; }
.mobile-preview-shell {
  width: 280px;
  max-width: 100%;
  height: 438px;
  margin: 0 auto;
  overflow-y: auto;
  color: var(--preview-text, #202735);
  background: var(--preview-page-bg, #f5f6f8);
  border: 6px solid #1f2937;
  border-radius: 25px;
  box-shadow: 0 12px 28px rgba(31, 41, 55, .16);
}
.mobile-preview-status { display:flex; justify-content:space-between; padding:7px 15px 3px; color:#1f2937; font-size:10px; font-weight:700; background:#fff; }
.mobile-preview-brand { display:grid; grid-template-columns:25px 1fr auto; align-items:center; gap:7px; padding:7px 12px 8px; background:var(--preview-header-bg, #fff); }
.mobile-preview-logo { display:grid; width:25px; height:25px; place-items:center; overflow:hidden; color:var(--preview-color); font-weight:800; background:#fff; border:2px solid var(--preview-color); border-radius:8px; }
.mobile-preview-logo img { width:100%; height:100%; object-fit:contain; }
.mobile-preview-brand strong { overflow:hidden; font-size:14px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-share { color:var(--preview-accent, var(--preview-color)); font-size:10px; }
.mobile-preview-search { display:grid; grid-template-columns:20px minmax(0,1fr) 23px; align-items:center; gap:5px; margin:10px 10px 8px; padding:6px 8px; overflow:hidden; color:#98a2b3; background:#fff; border:1.5px solid var(--preview-accent, var(--preview-color)); border-radius:999px; font-size:12px; white-space:nowrap; }
.mobile-preview-search b { display:grid; width:23px; height:23px; place-items:center; color:#fff; background:var(--preview-button, var(--preview-color)); border-radius:50%; }
.mobile-preview-banner { position:relative; display:grid; gap:4px; min-height:122px; margin:0 12px 10px; overflow:hidden; color:#fff; background:linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 84%, #111 16%), var(--preview-color)); border-radius:16px; }
.live-preview-banner img { width:100%; height:122px; object-fit:cover; }
.live-preview-banner i { position:absolute; right:12px; bottom:8px; margin:0; }
.preview-empty-module { display:grid; place-items:center; align-content:center; gap:4px; min-height:100px; padding:14px; color:#667085; font-size:11px; text-align:center; }
.preview-empty-module strong { color:var(--preview-color); font-size:15px; }
.mobile-preview-banner .preview-empty-module { color:#fff; }
.mobile-preview-banner .preview-empty-module strong { color:#fff; }
.mobile-preview-banner span { font-size:20px; font-weight:800; }
.mobile-preview-banner small { opacity:.86; }
.mobile-preview-banner i { margin-top:8px; font-style:normal; font-size:11px; letter-spacing:3px; opacity:.85; }
.mobile-preview-notice { display:flex; align-items:center; gap:7px; margin:0 12px 10px; padding:9px 10px; overflow:hidden; color:var(--preview-accent, var(--preview-color)); background:var(--preview-card-bg, #fff); border-radius:12px; }
.mobile-preview-notice small { overflow:hidden; color:var(--preview-muted, #98a2b3); text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-categories { display:flex; gap:7px; margin:0 12px 10px; padding:10px; overflow:hidden; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-category { flex:0 0 58px; display:grid; justify-items:center; gap:4px; min-width:0; color:var(--preview-color); font-size:10px; text-align:center; }
.mobile-preview-category > span { display:grid; width:36px; height:36px; place-items:center; overflow:hidden; background:color-mix(in srgb, var(--preview-color) 10%, #fff 90%); border-radius:50%; }
.mobile-preview-category img { width:100%; height:100%; object-fit:cover; }
.mobile-preview-category b { font-size:14px; }
.mobile-preview-category strong { overflow:hidden; width:100%; text-overflow:ellipsis; white-space:nowrap; }
.preview-empty-inline { padding:12px; color:var(--preview-muted, #98a2b3); font-size:11px; }
.mobile-preview-heading { display:grid; gap:2px; padding:5px 11px; }
.mobile-preview-heading strong { font-size:17px; }
.mobile-preview-heading span { color:#98a2b3; font-size:10px; }
.mobile-preview-page-title { display:grid; gap:4px; margin:0 12px 10px; padding:14px; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-page-title strong { font-size:20px; }
.mobile-preview-page-title strong small { color:var(--preview-muted, #98a2b3); font-size:12px; font-weight:500; }
.mobile-preview-page-title span { color:var(--preview-muted, #98a2b3); font-size:11px; }
.mobile-preview-category-grid { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:8px; margin:0 12px 10px; padding:10px; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-category-tile { display:grid; justify-items:center; gap:4px; min-width:0; color:var(--preview-text, #202735); font-size:10px; text-align:center; }
.mobile-preview-category-tile span { display:grid; width:38px; height:38px; place-items:center; overflow:hidden; color:var(--preview-color); background:color-mix(in srgb, var(--preview-color) 10%, #fff 90%); border-radius:12px; }
.mobile-preview-category-tile img { width:100%; height:100%; object-fit:cover; }
.mobile-preview-category-tile b { font-size:15px; }
.mobile-preview-category-tile strong { overflow:hidden; width:100%; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-cart-list { display:grid; gap:1px; margin:0 12px 10px; overflow:hidden; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-cart-item { display:grid; grid-template-columns:16px 48px minmax(0, 1fr) auto; align-items:center; gap:8px; padding:10px; border-bottom:1px solid color-mix(in srgb, var(--preview-line, #e5e7eb) 75%, transparent); }
.mobile-preview-cart-item > img,.mobile-preview-cart-item > i { display:block; width:48px; height:48px; object-fit:cover; background:linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 15%, #fff 85%), #e9edf2); border-radius:9px; }
.mobile-preview-cart-item > div { display:grid; gap:3px; min-width:0; }
.mobile-preview-cart-item strong,.mobile-preview-cart-item small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-cart-item strong { font-size:12px; }
.mobile-preview-cart-item small { color:var(--preview-muted, #98a2b3); font-size:10px; }
.mobile-preview-cart-item b { color:var(--preview-price, var(--preview-color)); font-size:12px; }
.mobile-preview-cart-item em { color:var(--preview-muted, #98a2b3); font-size:11px; font-style:normal; }
.cart-check { display:grid; width:16px; height:16px; place-items:center; color:#fff; font-size:10px; background:var(--preview-accent, var(--preview-color)); border-radius:50%; }
.mobile-preview-cart-summary { display:grid; grid-template-columns:1fr auto auto; align-items:center; gap:10px; margin:0 12px 14px; padding:11px 12px; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-cart-summary span { color:var(--preview-muted, #98a2b3); font-size:11px; }
.mobile-preview-cart-summary strong { color:var(--preview-price, var(--preview-color)); font-size:15px; }
.mobile-preview-cart-summary button { padding:7px 14px; color:#fff; background:var(--preview-button, var(--preview-color)); border:0; border-radius:999px; font-size:11px; }
.mobile-preview-profile-card { display:grid; grid-template-columns:42px minmax(0, 1fr) auto; align-items:center; gap:10px; margin:0 12px 10px; padding:14px; color:#fff; background:linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 82%, #111 18%), var(--preview-color)); border-radius:16px; }
.profile-avatar { display:grid; width:42px; height:42px; place-items:center; color:var(--preview-color); font-size:20px; font-weight:800; background:#fff; border-radius:50%; }
.mobile-preview-profile-card div:not(.profile-avatar) { display:grid; gap:4px; min-width:0; }
.mobile-preview-profile-card strong { font-size:15px; }
.mobile-preview-profile-card small { overflow:hidden; font-size:10px; opacity:.82; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-profile-card > span { font-size:22px; opacity:.85; }
.mobile-preview-order-card { margin:0 12px 10px; padding:4px 0 10px; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-order-card .mobile-preview-heading { display:flex; align-items:center; justify-content:space-between; }
.mobile-preview-order-card .mobile-preview-heading span { color:var(--preview-muted, #98a2b3); }
.mobile-preview-order-grid { display:grid; grid-template-columns:repeat(4, 1fr); gap:4px; padding:10px 8px 0; color:var(--preview-muted, #98a2b3); font-size:10px; text-align:center; }
.mobile-preview-service-grid { display:grid; grid-template-columns:repeat(4, 1fr); gap:1px; margin:0 12px 10px; padding:14px 6px; color:var(--preview-text, #202735); font-size:10px; text-align:center; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-profile-note { margin:0 12px 14px; padding:11px; color:var(--preview-muted, #98a2b3); font-size:10px; text-align:center; background:var(--preview-card-bg, #fff); border-radius:12px; }
.mobile-preview-products { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:7px; padding:5px 10px 10px; }
.mobile-preview-product { display:grid; gap:4px; min-width:0; padding:7px; background:var(--preview-card-bg, #fff); border-radius:12px; }
.mobile-preview-product img { display:block; width:100%; height:72px; object-fit:cover; border-radius:8px; }
.mobile-preview-product i { display:block; height:72px; background:linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 15%, #fff 85%), #e9edf2); border-radius:8px; }
.mobile-preview-product strong { overflow:hidden; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-product small { overflow:hidden; color:#98a2b3; font-size:9px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-product b { color:var(--preview-price, var(--preview-color)); font-size:13px; }
.mobile-preview-feature-row { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:7px; margin:0 10px 11px; }
.mobile-preview-feature-row.is-single { grid-template-columns:minmax(0,1fr); }
.mobile-preview-feature { min-width:0; }
.mobile-preview-feature>div { display:flex; align-items:center; justify-content:space-between; gap:4px; margin-bottom:5px; }
.mobile-preview-feature>div strong { font-size:11px; }
.mobile-preview-feature>div small { color:var(--preview-muted,#98a2b3); font-size:8px; }
.mobile-preview-feature section { position:relative; height:92px; display:flex; flex-direction:column; justify-content:flex-end; gap:3px; overflow:hidden; padding:9px; color:#fff; background:linear-gradient(145deg,#364152,#111827); border-radius:11px; }
.mobile-preview-new-arrivals section { background:linear-gradient(145deg,#7357e6,#44318d); }
.mobile-preview-feature section img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; opacity:.45; }
.mobile-preview-feature section b,.mobile-preview-feature section span { position:relative; z-index:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-feature section b { font-size:10px; }
.mobile-preview-feature section span { font-size:8px; opacity:.8; }
.mobile-preview-trust { display:grid; grid-template-columns:repeat(3,1fr); gap:1px; margin:0 12px 12px; padding:9px 4px; color:#667085; font-size:11px; text-align:center; background:var(--preview-card-bg, #fff); border-radius:12px; }
.mobile-preview-nav { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); padding:10px 8px 12px; color:#8a94a4; font-size:11px; text-align:center; background:#fff; border-top:1px solid #eef0f3; }
.mobile-preview-nav span.active { color:var(--preview-color); font-weight:800; }
.version-form {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.display-alert {
  flex: 0 0 auto;
  margin-bottom: 8px;
}
:global(.el-dialog.display-workbench-dialog),
:global(.display-workbench-dialog .el-dialog) {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 24px);
  max-height: 820px;
  margin: 12px auto 0;
  overflow: hidden;
  border-radius: 16px;
}
:global(.el-dialog.display-workbench-dialog .el-dialog__header),
:global(.display-workbench-dialog .el-dialog .el-dialog__header) {
  flex: 0 0 auto;
  margin-right: 0;
  padding: 14px 20px 11px;
  border-bottom: 1px solid #edf0f5;
}
:global(.el-dialog.display-workbench-dialog .el-dialog__title),
:global(.display-workbench-dialog .el-dialog .el-dialog__title) { font-size: 18px; }
:global(.el-dialog.display-workbench-dialog .el-dialog__body),
:global(.display-workbench-dialog .el-dialog .el-dialog__body) {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  padding: 10px 16px 12px;
  overflow: hidden;
  background: #f8fafc;
}
:global(.el-dialog.display-workbench-dialog .el-dialog__footer),
:global(.display-workbench-dialog .el-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  padding: 9px 16px;
  background: #fff;
  border-top: 1px solid #edf0f5;
}
.display-alert :deep(.el-alert__content) { min-width: 0; }
.display-alert :deep(.el-alert__title) { font-size: 12px; line-height: 18px; }
.workbench-heading { display:flex; flex:0 0 auto; align-items:center; justify-content:space-between; gap:12px; margin:0 0 8px; padding:0 2px; }
.workbench-heading > div:first-child { display:flex; align-items:baseline; gap:8px; }
.workbench-heading span { color:#98a2b3; font-size:12px; }
.workbench-heading strong { color:#1f2937; font-size:15px; }
.workbench-heading-meta { display:flex; align-items:center; gap:6px; color:#667085; font-size:12px; }
.draft-dot { display:inline-block; width:7px; height:7px; background:#67c23a; border-radius:50%; }
.visual-design-panel {
  margin-bottom: 8px;
  padding: 10px;
  background: linear-gradient(135deg, #fbfdff, #f5f8fc);
  border: 1px solid #e4ebf3;
  border-radius: 12px;
}
.visual-design-panel .control-section-heading { margin-bottom: 7px; }
.visual-design-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 14px;
  align-items: start;
}
.compact-theme-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px; margin: 0; }
.compact-theme-grid .theme-preset { grid-template-columns: 38px minmax(0, 1fr); gap: 0 6px; padding: 6px; border-radius: 8px; }
.compact-theme-grid .theme-preview { width: 38px; height: 30px; padding: 4px; }
.compact-theme-grid .theme-preset strong { font-size: 12px; }
.compact-theme-grid .theme-preset small { min-height: 0; overflow: hidden; font-size: 10px; line-height: 14px; text-overflow: ellipsis; white-space: nowrap; }
.visual-design-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 8px 12px; padding: 3px 0 0; }
.visual-design-fields .visual-design-field:first-child { grid-column: 1 / -1; }
.visual-design-field { display: grid; gap: 5px; color: #667085; font-size: 12px; }
.visual-design-fields .color-editor { grid-template-columns: 32px minmax(0, 1fr); width: 100%; gap: 7px; }
.visual-design-fields .color-editor .el-color-picker { width: 32px; }
.display-logo-editor { display: flex; align-items: center; gap: 8px; }
.display-logo-editor small { color: #98a2b3; font-size: 10px; line-height: 14px; }
.display-logo-uploader { display: grid; width: 42px; height: 42px; place-items: center; overflow: hidden; color: var(--el-color-primary); background: #f5f7fa; border: 1px dashed #cfd6e0; border-radius: 9px; cursor: pointer; }
.display-logo-uploader .el-image { width: 100%; height: 100%; }
.display-logo-uploader span { font-size: 11px; }
.display-workbench {
  display: grid;
  grid-template-columns: 145px minmax(0, 1fr) 285px;
  flex: 1 1 auto;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}
.workbench-nav { display:flex; flex-direction:column; gap:6px; padding:4px; overflow-y:auto; background:#f3f6fa; border:1px solid #e5eaf1; border-radius:12px; }
.workbench-nav button { display:grid; grid-template-columns:26px minmax(0,1fr); align-items:start; gap:8px; width:100%; padding:11px 9px; color:#6b7280; text-align:left; background:transparent; border:1px solid transparent; border-radius:9px; cursor:pointer; }
.workbench-nav button > span { display:grid; width:24px; height:24px; place-items:center; color:#8792a2; font-size:10px; background:#fff; border:1px solid #dfe5ed; border-radius:7px; }
.workbench-nav button div { display:grid; gap:3px; min-width:0; }
.workbench-nav button strong { color:#394150; font-size:13px; line-height:20px; }
.workbench-nav button small { color:#98a2b3; font-size:10px; line-height:14px; }
.workbench-nav button:hover,.workbench-nav button:focus-visible { background:#fff; border-color:#cad9eb; outline:none; }
.workbench-nav button.active { background:#fff; border-color:#a9c6e7; box-shadow:0 5px 12px rgba(21,86,163,.09); }
.workbench-nav button.active > span { color:#fff; background:#1556a3; border-color:#1556a3; }
.workbench-nav button.active strong { color:#1556a3; }
.display-controls {
  height: 100%;
  max-height: none;
  padding: 0 4px 0 0;
  overflow-y: auto;
}
.display-section-switcher {
  margin: 0 0 7px;
}
.display-section-brand-only {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 0 2px 6px;
  border-bottom: 1px solid #e7ebf2;
}
.display-section-brand-only strong { color: var(--el-color-primary); font-size: 14px; }
.display-section-brand-only small { margin-left: auto; color: #98a2b3; font-size: 11px; }
.brand-color-detail,.home-template-modules,.home-category-settings { margin-top:14px; padding-top:14px; border-top:1px solid #e8ecf1; }
.independent-page-hub { margin-bottom:8px; }
.independent-page-tabs { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; }
.independent-page-tabs button { display:grid; grid-template-columns:34px minmax(0,1fr); align-items:center; gap:9px; min-width:0; padding:10px; text-align:left; background:#f7f9fc; border:1px solid #e4e9f1; border-radius:10px; cursor:pointer; }
.independent-page-tabs button > span { display:grid; width:32px; height:32px; place-items:center; color:#1556a3; background:#eaf2fb; border-radius:9px; font-weight:700; }
.independent-page-tabs button div { display:grid; gap:2px; min-width:0; }
.independent-page-tabs button strong { color:#303846; font-size:12px; }
.independent-page-tabs button small { overflow:hidden; color:#8a94a4; font-size:10px; text-overflow:ellipsis; white-space:nowrap; }
.independent-page-tabs button.active { background:#f1f6fc; border-color:#1556a3; box-shadow:0 0 0 2px rgba(21,86,163,.08); }
.home-banner-settings { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-top:14px; padding:13px; background:#f7f9fc; border:1px solid #e7ebf1; border-radius:10px; }
.home-banner-settings > div { display:grid; gap:3px; }
.home-banner-settings strong { color:#303846; font-size:13px; }
.home-banner-settings small { color:#8a94a4; font-size:11px; }
.control-section {
  margin-bottom: 8px;
  padding: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}
.control-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.control-section-heading div { display: grid; gap: 3px; }
.control-section-heading strong { color: #303133; font-size: 14px; }
.control-section-heading small { color: #909399; font-size: 12px; }
.control-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: #606266;
  border-top: 1px solid #f2f3f5;
}
.drag-handle {
  flex: 0 0 18px;
  color: #a8abb2;
  font-size: 16px;
  line-height: 1;
  letter-spacing: -4px;
  cursor: grab;
}
.module-list-sortable .module-item,
.nav-list-sortable .nav-config-row { cursor: grab; }
.module-list-sortable .module-item:active,
.nav-list-sortable .nav-config-row:active { cursor: grabbing; }
.module-list-sortable .module-item { gap: 8px; min-height: 34px; padding: 7px 9px; }
.module-list-sortable .module-item strong { flex: 1; }
.sort-actions { display: inline-flex; gap: 0; white-space: nowrap; }
.sort-actions .el-button { padding: 2px 4px; font-size: 11px; }
.category-config-section .control-section-heading { margin-bottom: 8px; }
.category-list.category-list-draft {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 6px;
  overflow: visible;
  background: #f7f9fc;
  border: 1px solid #eef1f5;
  border-radius: 9px;
}
.category-list-draft .category-row {
  min-width: 0;
  gap: 6px;
  padding: 6px 8px;
  color: #475467;
  font-size: 12px;
  background: #fff;
  border-color: #e8edf3;
  border-radius: 7px;
}
.category-list-draft .category-row > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-list-draft .el-switch { transform: scale(.9); transform-origin: right center; }
.nav-config-row { gap: 8px; }
.nav-config-row .nav-type-name { width: 54px; color: #303133; font-size: 12px; }
.nav-config-row .el-switch { margin-left: auto; }
.color-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.color-grid label { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #606266; font-size: 12px; }
.preview-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  min-height: 0;
  padding: 10px 10px 9px;
  overflow: hidden;
  background: #f1f4f8;
  border: 1px solid #e4e9f0;
  border-radius: 14px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,.85);
}
.preview-stage-heading { display: flex; flex:0 0 auto; align-items: center; justify-content: space-between; width: 100%; gap: 10px; margin-bottom: 8px; }
.preview-stage-heading div { display: grid; gap: 3px; }
.preview-stage-heading strong { color: #303133; font-size: 14px; }
.preview-stage-heading span { color: #909399; font-size: 11px; }
.preview-coming-soon { display: grid; place-items: center; align-content: center; gap: 8px; flex: 1; width: 100%; min-height: 520px; color: #909399; text-align: center; background: #fff; border: 1px dashed #dcdfe6; border-radius: 14px; }
.preview-coming-soon strong { color: #606266; font-size: 18px; }
.preview-coming-soon span { font-size: 12px; }
.module-list,.category-list,.nav-config-list { display: grid; gap: 8px; }
.module-item,.category-row,.nav-config-row { display:flex; align-items:center; gap:12px; padding:10px 12px; border:1px solid #ebeef5; border-radius:8px; background:#fafbfc; }
.module-item strong,.category-row span { flex:1; color:#303133; }
.module-actions { display:flex; gap:5px; }
.section-note { margin-top:8px; color:#909399; font-size:12px; }
.category-row { justify-content:space-between; background:#fff; }
.nav-config-row > span { width:90px; color:#303133; }
.nav-config-row .el-switch { margin-left:auto; }
.color-editor { display:flex; align-items:center; gap:8px; }
.color-editor span { color:#909399; font-size:12px; font-family:monospace; }
.layout-template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.layout-template-card {
  min-width: 0;
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  grid-template-rows: auto auto;
  gap: 2px 8px;
  padding: 8px;
  color: #303133;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.layout-template-card:hover,
.layout-template-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
  transform: translateY(-1px);
}
.layout-template-card > strong { align-self:end; font-size:12px; }
.layout-template-card > small { min-height:28px; overflow:hidden; color:#909399; font-size:10px; line-height:14px; }
.layout-template-preview {
  grid-row: 1 / 3;
  display:grid;
  grid-template-rows:8px 12px 1fr 8px;
  gap:3px;
  width:68px;
  height:58px;
  padding:5px;
  overflow: hidden;
  background: #f3f5f7;
  border-radius:6px;
}
.layout-template-preview i { display:block; background:var(--el-color-primary); border-radius:3px; }
.layout-template-preview b { display:block; width:72%; background:#fff; border:1px solid #d9dfe6; border-radius:4px; }
.layout-template-preview em { display:block; background:repeating-linear-gradient(90deg,#fff 0 47%,transparent 47% 53%); border-radius:3px; }
.layout-template-preview small { display:block; min-height:0; background:#cdd6df; border-radius:3px; }
.layout-template-preview.preview-category-focus b { background:repeating-linear-gradient(90deg,#b9d8f5 0 20%,transparent 20% 25%); }
.layout-template-preview.preview-campaign-feed { grid-template-rows:8px 8px 1fr 8px; }
.layout-template-preview.preview-campaign-feed em { background:repeating-linear-gradient(180deg,#fff 0 44%,#ff7a1a 44% 52%,transparent 52% 58%); }
.feature-control-section { padding:12px; }
.feature-toggle-card { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:15px; background:#f7f9fc; border:1px solid #e4e9f1; border-radius:12px; }
.new-arrival-window-setting { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; gap:12px; margin-top:12px; padding:15px; background:#fffaf0; border:1px solid #f5dfb8; border-radius:12px; }
.new-arrival-window-setting>div:first-child { display:grid; gap:5px; }
.new-arrival-window-setting small { color:#8a6d3b; font-size:12px; line-height:1.55; }
.new-arrival-days { grid-column:1/-1; display:flex; align-items:center; gap:9px; color:#8a6d3b; font-size:12px; }
.culture-form { display:grid; gap:13px; margin-top:14px; }
.culture-form .visual-design-field { display:grid; gap:7px; }
.culture-form .visual-design-field>span { color:#344054; font-size:13px; font-weight:700; }
.mobile-preview-culture { display:flex; flex-direction:column; gap:8px; margin:10px; padding:10px; background:var(--preview-card-bg,#fff); border-radius:14px; }
.mobile-preview-culture>img { width:100%; height:118px; object-fit:cover; border-radius:10px; }
.mobile-preview-culture>span { align-self:flex-start; padding:3px 7px; color:var(--preview-color); background:color-mix(in srgb,var(--preview-color) 10%,#fff 90%); border-radius:999px; font-size:8px; }
.mobile-preview-culture h3 { margin:0; color:var(--preview-text,#202735); font-size:16px; }
.mobile-preview-culture small { color:var(--preview-muted,#98a2b3); font-size:10px; }
.mobile-preview-culture p { max-height:92px; overflow:hidden; margin:3px 0 0; color:var(--preview-text,#202735); font-size:9px; line-height:1.65; white-space:pre-line; }
.culture-detail-field { align-items:stretch; }
.culture-detail-toolbar { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; color:#6b7280; line-height:1.6; }
.culture-detail-list { display:grid; gap:8px; margin-top:10px; }
.culture-detail-item { display:grid; grid-template-columns:20px 64px minmax(0,1fr) auto; gap:10px; align-items:center; padding:8px; background:#f6f7f9; border:1px solid #e8ecf1; border-radius:12px; cursor:grab; }
.culture-detail-item:active { cursor:grabbing; }
.culture-detail-item img { display:block; width:64px; height:72px; object-fit:cover; background:#e8ecf1; border-radius:8px; }
.culture-detail-item div { display:flex; min-width:0; flex-direction:column; gap:4px; }
.culture-detail-item strong { color:#1b2430; font-size:13px; }
.culture-detail-item small,.culture-detail-empty { color:#6b7280; font-size:12px; }
.culture-detail-handle { color:#98a2b3; font-weight:800; letter-spacing:-2px; }
.mobile-preview-culture-details { overflow:hidden; margin:2px -10px -10px; line-height:0; }
.mobile-preview-culture-details>div { line-height:0; }
.mobile-preview-culture-details img { display:block; width:100%; height:auto; margin:0; border:0; }
.mobile-preview-culture-details span { display:none; padding:14px 8px; color:#6b7280; background:#f6f7f9; font-size:9px; line-height:1.4; text-align:center; }
.mobile-preview-culture-details img.is-error { display:none; }
.mobile-preview-culture-details img.is-error+span { display:block; }
.mobile-preview-culture.detail-first { gap:0; padding:0; overflow:hidden; }
.mobile-preview-culture.detail-first .mobile-preview-culture-details { margin:0; }
.mobile-preview-page-header { display:grid; grid-template-columns:28px 1fr 28px; align-items:center; height:42px; padding:0 10px; color:var(--preview-text,#202735); background:#fff; border-bottom:1px solid #edf0f4; text-align:center; }
.mobile-preview-page-header strong { overflow:hidden; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-page-header span { font-size:16px; }
.feature-toggle-copy { display:flex; align-items:center; gap:12px; min-width:0; }
.feature-toggle-copy>div { display:grid; gap:4px; min-width:0; }
.feature-toggle-copy strong { color:#303133; font-size:14px; }
.feature-toggle-copy small { color:#7b8494; font-size:11px; line-height:17px; }
.feature-toggle-icon { display:grid; flex:0 0 38px; width:38px; height:38px; place-items:center; color:#fff; background:linear-gradient(145deg,#ff3b55,#d8143c); border-radius:11px; font-size:17px; font-weight:900; }
.feature-toggle-icon.new-arrivals-icon { color:#fff; background:linear-gradient(145deg,#8a67ed,#5a3ac8); font-size:9px; letter-spacing:.5px; }
.feature-toggle-action { display:flex; align-items:center; gap:10px; flex:0 0 auto; }
.feature-toggle-action>span { min-width:52px; color:#8a94a4; font-size:12px; font-weight:700; text-align:right; }
.feature-toggle-action>span.enabled { color:#2f9e44; }
.campaign-preview-products { grid-template-columns:1fr; }
.campaign-preview-products .mobile-preview-product img,.campaign-preview-products .mobile-preview-product i { height:118px; }
.campaign-preview-band { padding:4px 6px; color:#fff; background:linear-gradient(90deg,#ef3d25,#ff8a18); border-radius:4px; font-size:8px; }
.layout-preview-standard .mobile-preview-product { border:1px solid var(--preview-line,#e8ecf1); }
.layout-preview-product-focus .mobile-preview-products { grid-template-columns:repeat(3,minmax(0,1fr)); gap:5px; }
.layout-preview-product-focus .mobile-preview-product { gap:3px; padding:5px; border-radius:9px; }
.layout-preview-product-focus .mobile-preview-product img,.layout-preview-product-focus .mobile-preview-product i { height:52px; border-radius:6px; }
.layout-preview-product-focus .mobile-preview-product small { display:none; }
.layout-preview-product-focus .mobile-preview-product strong { font-size:9px; }
.layout-preview-product-focus .mobile-preview-product b { font-size:11px; }
.layout-preview-category-focus .mobile-preview-categories { padding:12px 9px; background:linear-gradient(145deg,#fff,color-mix(in srgb,var(--preview-accent) 9%,#fff 91%)); border:1px solid color-mix(in srgb,var(--preview-accent) 18%,#fff 82%); }
.layout-preview-category-focus .mobile-preview-category > span { width:40px; height:40px; box-shadow:0 5px 12px rgba(38,45,51,.1); }
.layout-preview-campaign-feed .mobile-preview-categories { padding:7px 9px; background:transparent; border-radius:0; }
.layout-preview-campaign-feed .mobile-preview-category { flex-basis:auto; }
.layout-preview-campaign-feed .mobile-preview-category > span { display:none; }
.layout-preview-campaign-feed .mobile-preview-brand { background:color-mix(in srgb,var(--preview-color) 16%,#eafff3 84%); }
.category-guide-config { margin-top:16px; padding:14px; background:#f6f8fb; border:1px solid #e5eaf1; border-radius:14px; }
.category-guide-config.disabled { opacity:.72; }
.category-guide-template-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; }
.category-guide-template-grid button { display:grid; gap:5px; padding:12px; color:#1b2430; background:#fff; border:1px solid #e8ecf1; border-radius:12px; text-align:left; }
.category-guide-template-grid button.active { color:#1556a3; border-color:#1556a3; box-shadow:0 0 0 2px rgba(21,86,163,.1); }
.category-guide-template-grid button:disabled { cursor:not-allowed; }
.category-guide-template-grid small { color:#6b7280; font-size:10px; line-height:1.5; }
.guide-module-switches { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; margin-top:10px; }
.guide-module-switches>div { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:9px 10px; background:#fff; border:1px solid #e8ecf1; border-radius:10px; font-size:12px; }
.guide-module-error { margin:8px 2px 0; color:#c2413b; font-size:12px; line-height:1.5; }
.module-dependent-switch { min-width:190px; display:flex; align-items:flex-end; flex-direction:column; gap:4px; }
.module-dependent-switch small { max-width:220px; color:#b26a00; font-size:10px; text-align:right; }
.nav-scope-note { margin:0 0 10px; color:#8a94a4; }
.mobile-category-guide-preview { padding:8px; color:var(--preview-text); background:var(--preview-page-bg); }
.mobile-category-guide-preview .mobile-preview-search { height:37px; min-height:37px; margin:0 0 8px; border-color:var(--preview-accent); line-height:1; }
.mobile-category-guide-preview .mobile-preview-search b { color:#fff; background:var(--preview-accent); }
.mobile-category-guide-preview h3 { margin:8px 2px; font-size:14px; }
.guide-preview-directory { display:block; }
.guide-preview-directory-body { width:100%; }
.guide-preview-directory-body.is-split { display:grid; grid-template-columns:62px minmax(0,1fr); align-items:start; gap:6px; }
.guide-preview-directory-body.is-content-only,.guide-preview-directory-body.is-primary-only { display:block; }
.guide-preview-directory-body>aside { overflow:hidden; background:#fff; border-radius:8px; }
.guide-preview-directory-body>aside span { display:block; padding:10px 3px; border-bottom:1px solid #e8ecf1; font-size:8px; text-align:center; }
.guide-preview-directory-body>aside span:first-child { color:var(--preview-accent); border-left:3px solid var(--preview-accent); font-weight:800; }
.guide-preview-directory-body>main { min-width:0; padding:6px; background:#fff; border-radius:8px; }
.guide-preview-directory-body main>section { margin-top:9px; }
.guide-preview-directory-body main>section:first-child { margin-top:0; }
.guide-preview-directory-body main>section>b,.mobile-category-guide-preview section>b { font-size:10px; }
.guide-preview-directory-body main>section>div:not(.preview-guide-products) { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:4px; margin-top:5px; }
.guide-preview-directory-body main>section>div>span { padding:6px 3px; background:#f6f7f9; border-radius:5px; font-size:7px; text-align:center; }
.guide-preview-primary-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:6px; }
.guide-preview-primary-grid span { min-width:0; padding:14px 7px; overflow:hidden; color:#1b2430; background:#fff; border:1px solid #e8ecf1; border-radius:8px; font-size:9px; font-weight:700; text-align:center; text-overflow:ellipsis; white-space:nowrap; }
.guide-preview-primary-grid span:first-child { color:var(--preview-accent); border-color:var(--preview-accent); box-shadow:0 0 0 2px color-mix(in srgb,var(--preview-accent) 8%,transparent); }
.preview-guide-invalid { margin:0; padding:18px 12px; color:#6b7280; background:#fff; border:1px dashed #cfd6e2; border-radius:10px; font-size:11px; text-align:center; }
.preview-guide-products { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:5px; margin-top:6px; }
.preview-guide-products article { min-width:0; overflow:hidden; padding-bottom:5px; background:#fff; border:1px solid #e8ecf1; border-radius:7px; }
.preview-guide-products img { width:100%; height:64px; object-fit:cover; }
.preview-guide-products span,.preview-guide-products strong { display:block; overflow:hidden; margin:3px 4px 0; font-size:7px; text-overflow:ellipsis; white-space:nowrap; }
.preview-guide-products strong { color:var(--preview-price); font-size:9px; }
.preview-guide-showcase { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:6px; }
.preview-guide-showcase article { position:relative; height:108px; overflow:hidden; background:#e8ecf1; border-radius:8px; }
.preview-guide-showcase img { width:100%; height:100%; object-fit:cover; }
.preview-guide-showcase strong { position:absolute; left:7px; bottom:7px; color:#fff; font-size:10px; text-shadow:0 1px 3px rgba(0,0,0,.65); }
.preview-guide-tabs { display:flex; gap:5px; overflow:hidden; margin:8px 0; }
.preview-guide-tabs span { flex:0 0 auto; padding:5px 8px; background:#fff; border:1px solid #e8ecf1; border-radius:999px; font-size:7px; }
.preview-guide-tabs span:first-child { color:#fff; background:var(--preview-accent); }
.preview-guide-scenarios { display:grid; gap:6px; }
.preview-guide-scenarios article { position:relative; height:95px; overflow:hidden; background:#fff; border-radius:8px; }
.preview-guide-scenarios img { width:100%; height:100%; object-fit:cover; }
.preview-guide-scenarios article>span { position:absolute; inset:0; display:flex; flex-direction:column; justify-content:center; padding:10px 45% 10px 10px; background:linear-gradient(90deg,rgba(255,255,255,.95),transparent); }
.preview-guide-scenarios strong,.preview-guide-scenarios small { font-size:10px; }
.preview-guide-scenarios small { margin-top:3px; color:#6b7280; font-size:7px; }
@media (max-width: 700px) {
  .ui-preview-head { grid-template-columns: 28px minmax(0, 1fr) auto; }
  .ui-preview-search { display: none; }
  .ui-preview-products { grid-template-columns: 1fr; }
  .ui-preview-product { grid-template-columns: 72px 1fr; align-items: center; column-gap: 10px; }
  .ui-preview-product i { grid-row: span 3; height: 72px; }
}
.switch-with-help {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  line-height: 1.4;
}
.switch-with-help span {
  color: #909399;
  font-size: 12px;
}
.license-uploader {
  width: 180px;
  height: 126px;
  border: 1px dashed #c0c4cc;
  border-radius: 8px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: #fafafa;
}
.license-uploader .el-image { width: 100%; height: 100%; }
.table-logo {
  width: 58px;
  height: 42px;
}
.empty-logo {
  color: #909399;
  font-size: 12px;
}
.logo-upload-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.logo-uploader {
  width: 120px;
  height: 80px;
  border: 1px dashed #c0ccda;
  border-radius: 7px;
  overflow: hidden;
  cursor: pointer;
  background: #fafafa;
}
.logo-uploader:hover {
  border-color: #409eff;
}
.logo-uploader :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.logo-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.logo-help {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}
.field-with-help {
  width: 100%;
}
.field-with-help span,
.color-editor > span {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}
.theme-preset-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.theme-preset {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  grid-template-rows: auto auto;
  gap: 1px 12px;
  padding: 10px;
  color: #303133;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}
.theme-preset:hover,
.theme-preset.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}
.theme-preview {
  grid-row: 1 / 3;
  width: 82px;
  height: 54px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  padding: 7px;
  background: #f4f5f7;
  border-radius: var(--preview-radius);
}
.theme-preview i {
  grid-column: 1 / 3;
  height: 8px;
  background: var(--preview-color);
  border-radius: 4px;
}
.theme-preview b,
.theme-preview em {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: calc(var(--preview-radius) / 2);
}
.theme-preset strong { align-self: end; font-size: 14px; }
.theme-preset small { color: #909399; line-height: 17px; }
.color-editor {
  width: 100%;
  display: grid;
  grid-template-columns: 42px 130px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}
.color-editor > span { margin: 0; }
@media (max-width: 1080px) {
  .display-workbench { grid-template-columns:minmax(0,1fr) 310px; }
  .workbench-nav { grid-column:1 / -1; display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); overflow:visible; }
  .workbench-nav button { grid-template-columns:22px minmax(0,1fr); padding:8px 7px; }
  .workbench-nav button > span { width:20px; height:20px; }
  .workbench-nav button small { display:none; }
}
@media (max-width: 760px) {
  .toolbar { align-items: flex-start; flex-direction: column; }
  .toolbar-actions { width: 100%; }
  .current-decoration-card { grid-template-columns:1fr; gap:22px; padding:24px 20px; }
  .current-decoration-preview { display:none; }
  .current-decoration-layout { align-items:flex-start; flex-wrap:wrap; }
  .current-decoration-layout .el-tag { margin-left:0; }
  .layout-template-grid { grid-template-columns: 1fr; }
  .theme-preset-grid { grid-template-columns: 1fr; }
  .color-editor { grid-template-columns: 42px minmax(0, 1fr); }
  .color-editor > span { grid-column: 1 / 3; }
  .display-workbench { grid-template-columns: 1fr; }
  .workbench-nav { grid-column:auto; grid-template-columns:1fr 1fr; }
  .display-controls { max-height: none; overflow: visible; }
  .preview-stage { order: 2; }
  .visual-design-grid { grid-template-columns: 1fr; }
  .visual-design-fields { grid-template-columns: 1fr; }
  .visual-design-fields .visual-design-field:first-child { grid-column: auto; }
  .compact-theme-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .independent-page-tabs { grid-template-columns:1fr; }
  .category-guide-template-grid,.guide-module-switches { grid-template-columns:1fr; }
  .feature-toggle-card { align-items:flex-start; flex-direction:column; gap:12px; }
  .feature-toggle-action { align-self:flex-end; }
}
@media (max-width: 680px) {
  .category-list.category-list-draft { grid-template-columns: 1fr; }
}
</style>
