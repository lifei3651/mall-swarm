package com.macro.mall.distribution.controller;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.DmsShopAddress;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopBanner;
import com.macro.mall.distribution.entity.DmsShopCategory;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopNotice;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.entity.DmsFreightTemplate;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.ShopAddressService;
import com.macro.mall.distribution.service.ShopServiceAddressService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.TenantService;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.AdminMemberSecurityService;
import com.macro.mall.distribution.service.OrderShipmentService;
import com.macro.mall.distribution.service.OrderSpreadsheetService;
import com.macro.mall.distribution.service.OrderRealtimeService;
import com.macro.mall.distribution.service.FlashSaleService;
import com.macro.mall.distribution.service.LogisticsTrackingService;
import com.macro.mall.distribution.service.MerchantProductReviewService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.security.ShopSessionCookieService;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.ShopHomeVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.util.ShopPublicViewSanitizer;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import com.macro.mall.distribution.vo.ShopProfileVO;
import com.macro.mall.distribution.vo.ShopLegalConfigVO;
import com.macro.mall.distribution.vo.FreightQuoteVO;
import com.macro.mall.distribution.vo.PurchaseLimitCheckVO;
import com.macro.mall.distribution.vo.AdminMemberVO;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.OrderShipmentImportResultVO;
import com.macro.mall.distribution.vo.FlashSaleActivityVO;
import com.macro.mall.distribution.vo.ShopBusinessConfigVO;
import com.macro.mall.distribution.vo.ShopLogisticsTrackingVO;
import com.macro.mall.distribution.entity.DmsMerchantProductReview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import java.util.List;

@Tag(name = "ShopController", description = "用户商城前台")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    @Value("${shop.payment.simulation-enabled:false}")
    private boolean simulationPaymentEnabled;

    private final ShopService shopService;
    private final ShopAuthService authService;
    private final ShopAddressService addressService;
    private final ShopServiceAddressService serviceAddressService;
    private final ShopAfterSaleService afterSaleService;
    private final TenantService tenantService;
    private final AdminAuthService adminAuthService;
    private final AdminMemberSecurityService adminMemberSecurityService;
    private final OrderShipmentService orderShipmentService;
    private final OrderSpreadsheetService orderSpreadsheetService;
    private final ShopSessionCookieService shopSessionCookieService;
    private final OrderRealtimeService orderRealtimeService;
    private final FlashSaleService flashSaleService;
    private final LogisticsTrackingService logisticsTrackingService;
    private final MerchantProductReviewService merchantProductReviewService;

    @Operation(summary = "查询可选业务入口与当前会员复购资格")
    @GetMapping("/business-config")
    public CommonResult<ShopBusinessConfigVO> businessConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(shopService.getBusinessConfig(authService.resolveMember(authorization)));
    }

    @Operation(summary = "秒杀活动列表")
    @GetMapping("/flash-sales")
    public CommonResult<List<FlashSaleActivityVO>> flashSales() {
        return CommonResult.success(flashSaleService.listFront());
    }

    @Operation(summary = "提交秒杀订单")
    @PostMapping("/flash-sales/{activityId}/orders")
    @Idempotent(timeout = 30, message = "秒杀订单正在提交，请勿重复操作")
    public CommonResult<ShopOrderVO> submitFlashSale(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long activityId, @Valid @RequestBody ShopOrderSubmitDTO dto) {
        DmsShopMember member = authService.requireMember(authorization);
        if (member.getPayPasswordHash() == null || member.getPayPasswordHash().isBlank()) {
            Asserts.fail("首次交易前请先设置6位支付密码");
        }
        return CommonResult.success(ShopPublicViewSanitizer.order(flashSaleService.submit(activityId, dto, member)));
    }

    @Operation(summary = "复购商城商品列表")
    @GetMapping("/repurchase/products")
    public CommonResult<List<DmsShopProduct>> repurchaseProducts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String keyword) {
        return CommonResult.success(shopService.listRepurchaseProducts(keyword, authService.requireMember(authorization)));
    }

    @Operation(summary = "复购商品详情")
    @GetMapping("/repurchase/products/{id}")
    public CommonResult<ShopProductDetailVO> repurchaseProductDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(shopService.getRepurchaseProductDetail(id, authService.requireMember(authorization)));
    }

    @Operation(summary = "后台秒杀活动列表")
    @GetMapping("/admin/flash-sales")
    public CommonResult<List<FlashSaleActivityVO>> adminFlashSales(@RequestParam(required = false) Integer status) {
        return CommonResult.success(flashSaleService.listAdmin(status));
    }

    @Operation(summary = "创建秒杀活动")
    @PostMapping("/admin/flash-sales")
    public CommonResult<com.macro.mall.distribution.entity.DmsFlashSaleActivity> createFlashSale(
            @Valid @RequestBody FlashSaleActivitySaveDTO dto) {
        return CommonResult.success(flashSaleService.save(null, dto));
    }

    @Operation(summary = "更新秒杀活动")
    @PutMapping("/admin/flash-sales/{id}")
    public CommonResult<com.macro.mall.distribution.entity.DmsFlashSaleActivity> updateFlashSale(
            @PathVariable Long id, @Valid @RequestBody FlashSaleActivitySaveDTO dto) {
        return CommonResult.success(flashSaleService.save(id, dto));
    }

    @Operation(summary = "更新秒杀活动状态")
    @PutMapping("/admin/flash-sales/{id}/status")
    public CommonResult<Boolean> updateFlashSaleStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(flashSaleService.updateStatus(id, status));
    }

    @Operation(summary = "会员订单与售后状态实时通知")
    @GetMapping(value = "/events/orders", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orderEvents(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return orderRealtimeService.subscribeMember(authService.requireMember(authorization).getUserId());
    }

    @Operation(summary = "后台订单与售后待办实时通知")
    @GetMapping(value = "/admin/events/orders", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminOrderEvents() {
        if (AdminContext.get() == null) {
            Asserts.unauthorized("请先登录管理后台");
        }
        return orderRealtimeService.subscribeAdmin(TenantContext.getTenantId());
    }

    @Operation(summary = "商城账号注册（首笔有效支付后成为会员）")
    @PostMapping("/auth/register")
    public CommonResult<ShopAuthVO> register(@Valid @RequestBody ShopRegisterDTO dto,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        ShopAuthVO auth = authService.register(dto);
        shopSessionCookieService.write(request, response, auth.getToken(), auth.getExpireTime());
        return CommonResult.success(auth);
    }

    @Operation(summary = "公开 App/小程序账号注册（不建立团队关系）")
    @PostMapping("/public/auth/register")
    public CommonResult<ShopAuthVO> registerPublic(@Valid @RequestBody ShopRegisterDTO dto,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        ShopAuthVO auth = authService.registerPublic(dto);
        shopSessionCookieService.write(request, response, auth.getToken(), auth.getExpireTime());
        return CommonResult.success(auth);
    }

    @Operation(summary = "会员登录")
    @PostMapping("/auth/login")
    public CommonResult<ShopAuthVO> login(@Valid @RequestBody ShopLoginDTO dto,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        ShopAuthVO auth = authService.login(dto);
        shopSessionCookieService.write(request, response, auth.getToken(), auth.getExpireTime());
        return CommonResult.success(auth);
    }

    @Operation(summary = "当前会员")
    @GetMapping("/auth/me")
    public CommonResult<DmsShopMember> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(authService.me(authorization));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/auth/logout")
    public CommonResult<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        boolean loggedOut = authService.logout(authorization);
        shopSessionCookieService.clear(request, response);
        return CommonResult.success(loggedOut);
    }

    @Operation(summary = "会员自行设置登录账号和密码")
    @PutMapping("/auth/account")
    public CommonResult<DmsShopMember> setupAccount(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @Valid @RequestBody ShopAccountSetupDTO dto) {
        return CommonResult.success(authService.setupAccount(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "会员修改登录密码")
    @PutMapping("/auth/password")
    public CommonResult<Boolean> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @Valid @RequestBody ShopPasswordChangeDTO dto) {
        return CommonResult.success(authService.changePassword(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "会员修改昵称")
    @PutMapping("/auth/nickname")
    public CommonResult<DmsShopMember> updateNickname(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopNicknameUpdateDTO dto) {
        return CommonResult.success(authService.updateNickname(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "会员更换绑定手机号")
    @PutMapping("/auth/phone")
    public CommonResult<Boolean> updatePhone(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopPhoneUpdateDTO dto) {
        return CommonResult.success(authService.updatePhone(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "重置密码（忘记密码）")
    @PostMapping("/auth/resetPassword")
    public CommonResult<Boolean> resetPassword(@Valid @RequestBody ShopPasswordResetDTO dto) {
        authService.resetPassword(dto == null ? null : dto.getPhone(),
                dto == null ? null : dto.getSmsCode(), dto == null ? null : dto.getNewPassword());
        return CommonResult.success(true);
    }

    @Operation(summary = "后台解除会员密码错误锁定")
    @PutMapping("/admin/members/{id}/unlock")
    public CommonResult<Boolean> unlockMember(@PathVariable Long id) {
        return CommonResult.success(authService.unlockMember(id));
    }

    @Operation(summary = "后台新增商城会员")
    @PostMapping("/admin/members")
    public CommonResult<DmsShopMember> createAdminMember(@Valid @RequestBody AdminMemberCreateDTO dto) {
        if (Boolean.TRUE.equals(dto.getActivateDistribution()) || dto.getInitialLevel() != null) {
            adminAuthService.requirePermission(AdminContext.get(), "distribution:manage");
        }
        return CommonResult.success(authService.createAdminMember(dto));
    }

    @Operation(summary = "商城首页配置与推荐商品")
    @GetMapping("/home")
    public CommonResult<ShopHomeVO> home() {
        // 前台不接受 tenantId 参数，使用默认租户
        return CommonResult.success(shopService.getHome(null));
    }

    @Operation(summary = "商城经营主体、客服、备案及协议")
    @GetMapping("/legal-config")
    public CommonResult<ShopLegalConfigVO> legalConfig() {
        return CommonResult.success(ShopLegalConfigVO.from(tenantService.getTenant(1L)));
    }

    @Operation(summary = "商品分类")
    @GetMapping("/categories")
    public CommonResult<List<DmsShopCategory>> categories() {
        // 前台不接受 tenantId 参数，使用默认租户
        return CommonResult.success(shopService.listFrontCategories(null));
    }

    @Operation(summary = "后台分类列表")
    @GetMapping("/admin/categories")
    public CommonResult<List<DmsShopCategory>> adminCategories(@RequestParam(required = false) Long tenantId,
                                                              @RequestParam(required = false) Integer status) {
        return CommonResult.success(shopService.listAdminCategories(tenantId, status));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/admin/categories")
    public CommonResult<DmsShopCategory> createCategory(@Valid @RequestBody DmsShopCategory category) {
        return CommonResult.success(shopService.saveCategory(category));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/admin/categories/{id}")
    public CommonResult<DmsShopCategory> updateCategory(@PathVariable Long id, @Valid @RequestBody DmsShopCategory category) {
        return CommonResult.success(shopService.updateCategory(id, category));
    }

    @Operation(summary = "删除未使用的分类")
    @DeleteMapping("/admin/categories/{id}")
    public CommonResult<Boolean> deleteCategory(@PathVariable Long id) {
        return CommonResult.success(shopService.deleteCategory(id));
    }

    @Operation(summary = "启用/禁用分类")
    @PutMapping("/admin/categories/{id}/status")
    public CommonResult<Boolean> updateCategoryStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(shopService.updateCategoryStatus(id, status));
    }

    @Operation(summary = "分类首页展示开关")
    @PutMapping("/admin/categories/{id}/show-on-home")
    public CommonResult<Boolean> updateCategoryShowOnHome(@PathVariable Long id, @RequestParam Integer showOnHome) {
        return CommonResult.success(shopService.updateCategoryShowOnHome(id, showOnHome));
    }

    @Operation(summary = "后台轮播图列表")
    @GetMapping("/admin/banners")
    public CommonResult<List<DmsShopBanner>> adminBanners(@RequestParam(required = false) Long tenantId,
                                                          @RequestParam(required = false) Integer status) {
        return CommonResult.success(shopService.listAdminBanners(tenantId, status));
    }

    @Operation(summary = "新增轮播图")
    @PostMapping("/admin/banners")
    public CommonResult<DmsShopBanner> createBanner(@Valid @RequestBody DmsShopBanner banner) {
        return CommonResult.success(shopService.saveBanner(banner));
    }

    @Operation(summary = "更新轮播图")
    @PutMapping("/admin/banners/{id}")
    public CommonResult<DmsShopBanner> updateBanner(@PathVariable Long id, @Valid @RequestBody DmsShopBanner banner) {
        return CommonResult.success(shopService.updateBanner(id, banner));
    }

    @Operation(summary = "启用/禁用轮播图")
    @PutMapping("/admin/banners/{id}/status")
    public CommonResult<Boolean> updateBannerStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(shopService.updateBannerStatus(id, status));
    }

    @Operation(summary = "公告列表")
    @GetMapping("/notices")
    public CommonResult<List<DmsShopNotice>> notices() {
        // 前台不接受 tenantId 参数，使用默认租户，只返回有效公告
        return CommonResult.success(shopService.listActiveNotices(null));
    }

    @Operation(summary = "公告详情")
    @GetMapping("/notices/{id}")
    public CommonResult<DmsShopNotice> noticeDetail(@PathVariable Long id) {
        return CommonResult.success(shopService.getNotice(id));
    }

    @Operation(summary = "后台公告列表")
    @GetMapping("/admin/notices")
    public CommonResult<List<DmsShopNotice>> adminNotices(@RequestParam(required = false) Long tenantId,
                                                          @RequestParam(required = false) Integer status) {
        return CommonResult.success(shopService.listAdminNotices(tenantId, status));
    }

    @Operation(summary = "新增公告")
    @PostMapping("/admin/notices")
    public CommonResult<DmsShopNotice> createNotice(@Valid @RequestBody DmsShopNotice notice) {
        return CommonResult.success(shopService.saveNotice(notice));
    }

    @Operation(summary = "更新公告")
    @PutMapping("/admin/notices/{id}")
    public CommonResult<DmsShopNotice> updateNotice(@PathVariable Long id, @Valid @RequestBody DmsShopNotice notice) {
        return CommonResult.success(shopService.updateNotice(id, notice));
    }

    @Operation(summary = "启用/禁用公告")
    @PutMapping("/admin/notices/{id}/status")
    public CommonResult<Boolean> updateNoticeStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(shopService.updateNoticeStatus(id, status));
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/admin/notices/{id}")
    public CommonResult<Boolean> deleteNotice(@PathVariable Long id) {
        return CommonResult.success(shopService.deleteNotice(id));
    }

    @Operation(summary = "商品列表")
    @GetMapping("/products")
    public CommonResult<CommonPage<DmsShopProduct>> products(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String categoryName,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) String stockStatus,
                                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                                             @RequestParam(defaultValue = "12") Integer pageSize) {
        // 前台不接受 tenantId 参数，使用默认租户
        return CommonResult.success(shopService.listProductPage(
                null, keyword, categoryName, status, stockStatus, pageNum, pageSize));
    }

    @Operation(summary = "后台完整商品列表（包含复购专属商品）")
    @GetMapping("/admin/products")
    public CommonResult<CommonPage<DmsShopProduct>> adminProducts(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) String categoryName,
                                                                  @RequestParam(required = false) Integer status,
                                                                  @RequestParam(required = false) String stockStatus,
                                                                  @RequestParam(defaultValue = "1") Integer pageNum,
                                                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        return CommonResult.success(shopService.listAdminProductPage(null, keyword, categoryName, status, stockStatus, pageNum, pageSize));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/products/{id}")
    public CommonResult<ShopProductDetailVO> productDetail(@PathVariable Long id) {
        return CommonResult.success(shopService.getProductDetail(id));
    }

    @Operation(summary = "加入购物车前检查商品限购")
    @PostMapping("/products/{id}/purchase-limit/check")
    public CommonResult<PurchaseLimitCheckVO> checkPurchaseLimit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer quantity) {
        return CommonResult.success(shopService.checkPurchaseLimit(id, quantity, authService.requireMember(authorization)));
    }

    @Operation(summary = "创建商品")
    @PostMapping("/admin/products")
    public CommonResult<DmsShopProduct> createProduct(@Valid @RequestBody DmsShopProduct product) {
        return CommonResult.success(shopService.saveProduct(product));
    }

    @Operation(summary = "商品与SKU一次事务发布")
    @PostMapping("/admin/products/publish")
    public CommonResult<DmsShopProduct> publishProduct(@Valid @RequestBody ProductPublishDTO dto) {
        return CommonResult.success(shopService.publishProduct(null, dto));
    }

    @Operation(summary = "商品与SKU一次事务更新")
    @PutMapping("/admin/products/{id}/publish")
    public CommonResult<DmsShopProduct> publishProduct(@PathVariable Long id,
                                                        @Valid @RequestBody ProductPublishDTO dto) {
        return CommonResult.success(shopService.publishProduct(id, dto));
    }

    @Operation(summary = "更新商品")
    @PutMapping("/admin/products/{id}")
    public CommonResult<DmsShopProduct> updateProduct(@PathVariable Long id, @Valid @RequestBody DmsShopProduct product) {
        return CommonResult.success(shopService.updateProduct(id, product));
    }

    @Operation(summary = "上下架商品")
    @PutMapping("/admin/products/{id}/status")
    public CommonResult<Boolean> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(shopService.updateProductStatus(id, status));
    }

    @Operation(summary = "商户提交商品上架或变更审核")
    @PostMapping("/admin/products/{id}/submit-review")
    public CommonResult<DmsMerchantProductReview> submitMerchantProductReview(@PathVariable Long id) {
        return CommonResult.success(merchantProductReviewService.submit(id));
    }

    @Operation(summary = "商户商品审核队列")
    @GetMapping("/admin/merchant-product-reviews")
    public CommonResult<CommonPage<DmsMerchantProductReview>> merchantProductReviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return CommonResult.success(merchantProductReviewService.list(status, keyword, pageNum, pageSize));
    }

    @Operation(summary = "审核商户商品并在通过后自动上架")
    @PutMapping("/admin/merchant-product-reviews/{id}/decision")
    public CommonResult<DmsMerchantProductReview> decideMerchantProductReview(
            @PathVariable Long id, @Valid @RequestBody MerchantProductReviewDecisionDTO dto) {
        return CommonResult.success(merchantProductReviewService.decide(id, dto));
    }

    @Operation(summary = "商城发货/退货地址列表")
    @GetMapping("/admin/service-addresses")
    public CommonResult<List<DmsShopServiceAddress>> serviceAddresses(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Integer addressType,
            @RequestParam(required = false, defaultValue = "1") Integer status) {
        return CommonResult.success(serviceAddressService.list(tenantId, addressType, status));
    }

    @Operation(summary = "保存商城发货/退货地址")
    @PostMapping("/admin/service-addresses")
    public CommonResult<DmsShopServiceAddress> saveServiceAddress(@Valid @RequestBody DmsShopServiceAddress address) {
        return CommonResult.success(serviceAddressService.save(address));
    }

    @Operation(summary = "停用或设为默认商城地址")
    @PutMapping("/admin/service-addresses/{id}/status")
    public CommonResult<Boolean> updateServiceAddressStatus(@PathVariable Long id,
                                                             @RequestParam Integer status,
                                                             @RequestParam(required = false) Long tenantId) {
        return CommonResult.success(serviceAddressService.updateStatus(id, tenantId, status));
    }

    @Operation(summary = "商品发布设置")
    @GetMapping("/admin/product-settings")
    public CommonResult<DmsTenantDisplayConfig> productSettings() {
        return CommonResult.success(tenantService.getDisplayConfig(1L));
    }

    @Operation(summary = "开启或关闭商品PV填写")
    @PutMapping("/admin/product-settings/pv")
    public CommonResult<DmsTenantDisplayConfig> updateProductPvSetting(@RequestParam Boolean enabled) {
        DmsTenantDisplayConfig config = tenantService.getDisplayConfig(1L);
        config.setTenantId(1L);
        config.setShowPv(Boolean.TRUE.equals(enabled) ? 1 : 0);
        return CommonResult.success(tenantService.saveDisplayConfig(config));
    }

    @Operation(summary = "后台会员列表")
    @GetMapping("/admin/members")
    public CommonResult<CommonPage<AdminMemberVO>> adminMembers(@RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) Integer status,
                                                                @RequestParam(required = false) Integer promotionActivated,
                                                                @RequestParam(required = false) Integer agentLevel,
                                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(
                authService.listAdminMembers(keyword, status, promotionActivated, agentLevel)));
    }

    @Operation(summary = "后台会员全景详情")
    @GetMapping("/admin/members/{id}/profile")
    public CommonResult<ShopProfileVO> adminMemberProfile(@PathVariable Long id) {
        return CommonResult.success(shopService.getAdminProfile(authService.getAdminMember(id)));
    }

    @Operation(summary = "后台启用/禁用会员")
    @PutMapping("/admin/members/{id}/status")
    public CommonResult<Boolean> updateMemberStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(authService.updateMemberStatus(id, status));
    }

    @Operation(summary = "后台修改会员登录手机号")
    @PutMapping("/admin/members/{id}/phone")
    public CommonResult<Boolean> updateMemberPhone(@PathVariable Long id,
                                                    @Valid @RequestBody AdminMemberPhoneUpdateDTO dto) {
        adminAuthService.verifyPassword(AdminContext.get(), dto == null ? null : dto.getAdminPassword());
        return CommonResult.success(adminMemberSecurityService.updatePhone(id, dto));
    }

    @Operation(summary = "后台重置会员登录密码")
    @PutMapping("/admin/members/{id}/login-password")
    public CommonResult<Boolean> resetMemberLoginPassword(@PathVariable Long id,
                                                           @Valid @RequestBody AdminMemberPasswordResetDTO dto) {
        adminAuthService.verifyPassword(AdminContext.get(), dto == null ? null : dto.getAdminPassword());
        return CommonResult.success(adminMemberSecurityService.resetLoginPassword(id, dto));
    }

    @Operation(summary = "后台解除会员支付密码错误锁定")
    @PutMapping("/admin/members/{id}/payment-password/unlock")
    public CommonResult<Boolean> unlockMemberPaymentPassword(@PathVariable Long id) {
        return CommonResult.success(adminMemberSecurityService.unlockPaymentPassword(id));
    }

    @Operation(summary = "统一会员列表开通推广身份或直接调级")
    @PutMapping("/admin/members/{id}/level")
    public CommonResult<AgentInfoVO> adjustMemberLevel(@PathVariable Long id,
                                                       @Valid @RequestBody AgentLevelAdjustDTO dto) {
        return CommonResult.success(authService.adjustMemberLevel(id, dto.getLevel(), dto.getReason()));
    }

    @Operation(summary = "查询商品SKU")
    @GetMapping("/products/{productId}/skus")
    public CommonResult<List<DmsShopSku>> listSkus(@PathVariable Long productId,
                                                   @RequestParam(required = false) Integer status) {
        return CommonResult.success(shopService.listSkus(productId, status));
    }

    @Operation(summary = "后台查询商品完整SKU")
    @GetMapping("/admin/products/{productId}/skus")
    public CommonResult<List<DmsShopSku>> listAdminSkus(@PathVariable Long productId,
                                                        @RequestParam(required = false) Integer status) {
        return CommonResult.success(shopService.listAdminSkus(productId, status));
    }

    @Operation(summary = "新增SKU")
    @PostMapping("/admin/skus")
    public CommonResult<DmsShopSku> createSku(@Valid @RequestBody ShopSkuDTO dto) {
        return CommonResult.success(shopService.saveSku(dto));
    }

    @Operation(summary = "更新SKU")
    @PutMapping("/admin/skus/{id}")
    public CommonResult<DmsShopSku> updateSku(@PathVariable Long id, @Valid @RequestBody ShopSkuDTO dto) {
        return CommonResult.success(shopService.updateSku(id, dto));
    }

    @Operation(summary = "上下架SKU")
    @PutMapping("/admin/skus/{id}/status")
    public CommonResult<Boolean> updateSkuStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(shopService.updateSkuStatus(id, status));
    }

    @Operation(summary = "运费模板列表")
    @GetMapping("/admin/freight-templates")
    public CommonResult<List<DmsFreightTemplate>> freightTemplates(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Integer status) {
        return CommonResult.success(shopService.listFreightTemplates(tenantId, status));
    }

    @Operation(summary = "新增运费模板")
    @PostMapping("/admin/freight-templates")
    public CommonResult<DmsFreightTemplate> createFreightTemplate(@Valid @RequestBody FreightTemplateSaveDTO dto) {
        return CommonResult.success(shopService.saveFreightTemplate(dto));
    }

    @Operation(summary = "更新运费模板")
    @PutMapping("/admin/freight-templates/{id}")
    public CommonResult<DmsFreightTemplate> updateFreightTemplate(
            @PathVariable Long id, @Valid @RequestBody FreightTemplateSaveDTO dto) {
        return CommonResult.success(shopService.updateFreightTemplate(id, dto));
    }

    @Operation(summary = "会员地址列表")
    @GetMapping("/addresses")
    public CommonResult<List<DmsShopAddress>> addresses(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(addressService.list(authService.requireMember(authorization)));
    }

    @Operation(summary = "保存会员地址")
    @PostMapping("/addresses")
    public CommonResult<DmsShopAddress> saveAddress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopAddressDTO dto) {
        return CommonResult.success(addressService.save(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "删除会员地址")
    @DeleteMapping("/addresses/{id}")
    public CommonResult<Boolean> deleteAddress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(addressService.delete(authService.requireMember(authorization), id));
    }

    @Operation(summary = "提交前台订单")
    @PostMapping("/orders")
    @Idempotent(timeout = 30, message = "订单正在提交，请勿重复操作")
    public CommonResult<ShopOrderVO> submitOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopOrderSubmitDTO dto) {
        DmsShopMember member = authService.requireMember(authorization);
        if (member.getPayPasswordHash() == null || member.getPayPasswordHash().isBlank()) {
            Asserts.fail("首次交易前请先设置6位支付密码");
        }
        // 强制使用当前登录用户的 userId，忽略 DTO 中的值
        dto.setUserId(member.getUserId());
        // 通过服务层查询当前用户的代理人信息
        dto.setAgentId(shopService.resolveAgentId(member.getUserId()));
        return CommonResult.success(ShopPublicViewSanitizer.order(shopService.submitOrder(dto, member)));
    }

    @Operation(summary = "试算运费（运费不计入奖金与业绩）")
    @PostMapping("/orders/freight-quote")
    public CommonResult<FreightQuoteVO> freightQuote(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopOrderSubmitDTO dto) {
        DmsShopMember member = authService.requireMember(authorization);
        dto.setUserId(member.getUserId());
        return CommonResult.success(shopService.quoteFreight(dto, member));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/orders/{orderId}")
    public CommonResult<ShopOrderVO> orderDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        DmsShopMember member = authService.requireMember(authorization);
        ShopOrderVO vo = shopService.getOrder(orderId);
        if (!member.getUserId().equals(vo.getOrder().getUserId())) {
            Asserts.fail("不能查看他人的订单");
        }
        applyFrontOrderVisibility(vo);
        return CommonResult.success(vo);
    }

    @Operation(summary = "查询订单真实物流轨迹")
    @GetMapping("/orders/{orderId}/tracking")
    public CommonResult<List<ShopLogisticsTrackingVO>> orderTracking(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        DmsShopMember member = authService.requireMember(authorization);
        ShopOrderVO vo = shopService.getOrder(orderId);
        if (vo == null || vo.getOrder() == null || !member.getUserId().equals(vo.getOrder().getUserId())) {
            Asserts.fail("不能查看他人的订单物流");
        }
        return CommonResult.success(logisticsTrackingService.query(vo.getShipments()));
    }

    @Operation(summary = "取消订单")
    @PutMapping("/orders/{orderId}/cancel")
    public CommonResult<Boolean> cancelOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        return CommonResult.success(shopService.cancelOrder(orderId, authService.requireMember(authorization)));
    }

    @Operation(summary = "开发环境模拟支付订单（正式支付请使用微信、支付宝或余额渠道）")
    @PostMapping("/orders/{orderId}/pay")
    public CommonResult<ShopOrderVO> payOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "simulated") String payType) {
        if (!simulationPaymentEnabled) {
            return CommonResult.failed("模拟支付未启用，请使用正式支付渠道");
        }
        if (!"simulated".equals(payType)) {
            return CommonResult.failed("该接口仅用于开发环境模拟支付");
        }
        DmsShopMember member = authService.requireMember(authorization);
        ShopOrderVO vo = shopService.getOrder(orderId);
        if (!member.getUserId().equals(vo.getOrder().getUserId())) {
            Asserts.fail("不能支付他人的订单");
        }
        return CommonResult.success(ShopPublicViewSanitizer.order(shopService.markOrderPaid(orderId, payType)));
    }

    @Operation(summary = "确认收货")
    @PutMapping("/orders/{orderId}/receive")
    public CommonResult<Boolean> confirmReceive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        return CommonResult.success(shopService.confirmReceive(orderId, authService.requireMember(authorization)));
    }

    @Operation(summary = "查询前台订单列表")
    @GetMapping("/orders")
    public CommonResult<CommonPage<ShopOrderVO>> orders(@RequestParam(required = false) Long userId,
                                                        @RequestParam(required = false) Long agentId,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestParam(required = false) String orderState,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        DmsShopMember member = authService.requireMember(authorization);
        int safePageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        int safePageSize = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 50));
        PageHelper.startPage(safePageNum, safePageSize);
        List<ShopOrderVO> orders = shopService.listOrders(member.getUserId(), null, orderState);
        orders.forEach(this::applyFrontOrderVisibility);
        return CommonResult.success(CommonPage.restPage(orders));
    }

    @Operation(summary = "后台订单列表")
    @GetMapping("/admin/orders")
    public CommonResult<CommonPage<ShopOrderVO>> adminOrders(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) String orderState,
                                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(shopService.listAdminOrders(keyword, status, orderState)));
    }

    @Operation(summary = "后台待办订单数量")
    @GetMapping("/admin/orders/work-summary")
    public CommonResult<ShopOrderStatusSummaryVO> adminOrderWorkSummary() {
        return CommonResult.success(shopService.getAdminOrderWorkSummary());
    }

    @Operation(summary = "平台查看联合支付父交易及全部商户子订单")
    @GetMapping("/admin/trades/{tradeId}")
    public CommonResult<com.macro.mall.distribution.vo.ShopTradeDetailVO> adminTrade(@PathVariable Long tradeId) {
        return CommonResult.success(shopService.getAdminTrade(tradeId));
    }

    @Operation(summary = "导出筛选后的商城订单")
    @GetMapping("/admin/orders/export")
    public void exportAdminOrders(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Integer status,
                                  @RequestParam(required = false) String orderState,
                                  HttpServletResponse response) throws IOException {
        prepareExcelDownload(response, "商城订单-" + LocalDate.now() + ".xlsx");
        orderSpreadsheetService.writeOrderExport((pageNum, pageSize) -> {
            PageHelper.startPage(pageNum, pageSize, false);
            try {
                return shopService.listAdminOrders(keyword, status, orderState);
            } finally {
                PageHelper.clearPage();
            }
        }, response.getOutputStream());
    }

    @Operation(summary = "下载待发货订单物流回填模板")
    @GetMapping("/admin/orders/shipment-template")
    public void downloadShipmentTemplate(@RequestParam(required = false) String keyword,
                                         HttpServletResponse response) throws IOException {
        List<ShopOrderVO> orders;
        PageHelper.startPage(1, OrderSpreadsheetService.MAX_SHIPMENT_TEMPLATE_ROWS + 1, false);
        try {
            orders = shopService.listAdminOrders(keyword, 1, null);
        } finally {
            PageHelper.clearPage();
        }
        if (orders.size() > OrderSpreadsheetService.MAX_SHIPMENT_TEMPLATE_ROWS) {
            Asserts.fail("待发货订单超过2000条，请先使用订单号、收货人或手机号筛选后分批下载");
        }
        prepareExcelDownload(response, "待发货订单物流回填-" + LocalDate.now() + ".xlsx");
        orderSpreadsheetService.writeShipmentTemplate(orders, response.getOutputStream());
    }

    @Operation(summary = "下载物流发货导入空白模板")
    @GetMapping("/admin/orders/shipments/import-template")
    public void downloadShipmentImportTemplate(HttpServletResponse response) throws IOException {
        prepareExcelDownload(response, "物流发货导入模板.xlsx");
        orderSpreadsheetService.writeShipmentImportTemplate(response.getOutputStream());
    }

    @Operation(summary = "Excel批量导入订单物流信息并发货")
    @PostMapping("/admin/orders/shipments/import")
    public CommonResult<OrderShipmentImportResultVO> importOrderShipments(@RequestParam("file") MultipartFile file) {
        return CommonResult.success(orderShipmentService.importShipments(file));
    }

    @Operation(summary = "后台订单发货")
    @PutMapping("/admin/orders/{orderId}/ship")
    public CommonResult<Boolean> shipOrder(@PathVariable Long orderId, @Valid @RequestBody ShopOrderShipDTO dto) {
        return CommonResult.success(shopService.shipOrder(orderId, dto));
    }

    @Operation(summary = "后台查询订单真实物流轨迹")
    @GetMapping("/admin/orders/{orderId}/tracking")
    public CommonResult<List<ShopLogisticsTrackingVO>> adminOrderTracking(@PathVariable Long orderId) {
        ShopOrderVO vo = shopService.getOrder(orderId);
        if (vo == null || vo.getOrder() == null) Asserts.fail("订单不存在或无权查看");
        return CommonResult.success(logisticsTrackingService.query(vo.getShipments()));
    }

    @Operation(summary = "保存订单客服内部备注")
    @PutMapping("/admin/orders/{orderId}/service-remark")
    public CommonResult<Boolean> updateOrderServiceRemark(@PathVariable Long orderId,
                                                           @Valid @RequestBody ShopOrderServiceRemarkDTO dto) {
        return CommonResult.success(shopService.updateOrderServiceRemark(orderId, dto.getServiceRemark()));
    }

    @Operation(summary = "后台取消待付款或待发货订单")
    @PutMapping("/admin/orders/{orderId}/cancel")
    public CommonResult<Boolean> cancelAdminOrder(@PathVariable Long orderId) {
        if (AdminContext.get() != null && AdminContext.get().getMerchantId() != null) {
            Asserts.fail("商户不能执行平台取消或整单退款，请处理正常发货和客户售后");
        }
        ShopOrderVO order = shopService.getOrder(orderId);
        if (order == null || order.getOrder() == null) {
            Asserts.fail("订单不存在");
        }
        if (Integer.valueOf(0).equals(order.getOrder().getStatus())) {
            return CommonResult.success(shopService.cancelOrder(orderId, null));
        }
        if (Integer.valueOf(1).equals(order.getOrder().getStatus())) {
            Long operatorId = AdminContext.get() == null ? null : AdminContext.get().getId();
            String operatorName = AdminContext.get() == null ? "管理员" : AdminContext.get().getNickname();
            return CommonResult.success(afterSaleService.cancelPendingShipment(orderId, operatorId, operatorName));
        }
        Asserts.fail("只有待付款或待发货订单可以取消");
        return CommonResult.success(false);
    }

    @Operation(summary = "后台处理超期退款")
    @PostMapping("/admin/orders/{orderId}/refund")
    public CommonResult<DmsShopAfterSale> manualRefund(@PathVariable Long orderId,
                                                       @Valid @RequestBody ShopManualRefundDTO dto) {
        if (AdminContext.get() != null && AdminContext.get().getMerchantId() != null) {
            Asserts.fail("商户不能发起平台人工退款，请通过客户售后流程处理");
        }
        return CommonResult.success(afterSaleService.manualRefund(orderId, dto));
    }

    private void prepareExcelDownload(HttpServletResponse response, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("Cache-Control", "no-store");
    }

    @Operation(summary = "用户/代理前台个人中心")
    @GetMapping("/profile")
    public CommonResult<ShopProfileVO> profile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        DmsShopMember member = authService.requireMember(authorization);
        // 只查询当前会员自己的资料，不接受外部 agentId 参数
        return CommonResult.success(shopService.getProfile(member, null));
    }

    @Operation(summary = "公开 App/小程序个人中心（不返回团队、等级、业绩和奖金资料）")
    @GetMapping("/public/profile")
    public CommonResult<ShopProfileVO> publicProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        DmsShopMember member = authService.requireMember(authorization);
        ShopProfileVO profile = new ShopProfileVO();
        profile.setMember(authService.me(authorization));
        profile.setOrderSummary(shopService.getOrderStatusSummary(member));
        return CommonResult.success(profile);
    }

    @Operation(summary = "个人中心订单状态数量")
    @GetMapping("/profile/order-summary")
    public CommonResult<ShopOrderStatusSummaryVO> profileOrderSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(shopService.getOrderStatusSummary(authService.requireMember(authorization)));
    }

    @Operation(summary = "个人中心团队业绩")
    @GetMapping("/profile/performance")
    public CommonResult<ShopProfileVO> profilePerformance(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(shopService.getProfilePerformance(authService.requireMember(authorization)));
    }

    @Operation(summary = "获取邀请信息")
    @GetMapping("/invite/my")
    public CommonResult<java.util.Map<String, Object>> myInvite(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        DmsShopMember member = authService.requireMember(authorization);
        return CommonResult.success(shopService.getInviteInfo(member));
    }

    @Operation(summary = "团队H5首次绑定直属邀请关系")
    @PostMapping("/team/invitation")
    public CommonResult<DmsShopMember> bindTeamInvitation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopInviteBindDTO dto) {
        return CommonResult.success(authService.bindInviter(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "注册页查询脱敏邀请人信息")
    @GetMapping("/invite/{inviteCode}")
    public CommonResult<java.util.Map<String, Object>> inviterPreview(@PathVariable String inviteCode) {
        return CommonResult.success(shopService.getInviterPreview(inviteCode));
    }

    @Operation(summary = "申请售后")
    @PostMapping("/after-sales")
    public CommonResult<DmsShopAfterSale> applyAfterSale(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopAfterSaleApplyDTO dto) {
        return CommonResult.success(afterSaleService.apply(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "会员取消待审核售后")
    @PutMapping("/after-sales/{id}/cancel")
    public CommonResult<DmsShopAfterSale> cancelAfterSale(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(afterSaleService.cancel(authService.requireMember(authorization), id));
    }

    @Operation(summary = "会员提交退货物流")
    @PutMapping("/after-sales/{id}/return-shipment")
    public CommonResult<DmsShopAfterSale> submitReturnShipment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody ShopAfterSaleReturnShipmentDTO dto) {
        return CommonResult.success(afterSaleService.submitReturnShipment(authService.requireMember(authorization), id, dto));
    }

    @Operation(summary = "我的售后")
    @GetMapping("/after-sales")
    public CommonResult<List<DmsShopAfterSale>> myAfterSales(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(afterSaleService.listByMember(authService.requireMember(authorization)));
    }

    @Operation(summary = "后台售后列表")
    @GetMapping("/admin/after-sales")
    public CommonResult<CommonPage<DmsShopAfterSale>> adminAfterSales(@RequestParam(required = false) String keyword,
                                                                     @RequestParam(required = false) Integer status,
                                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(afterSaleService.listAdmin(keyword, status)));
    }

    @Operation(summary = "后台审核售后")
    @PutMapping("/admin/after-sales/{id}/audit")
    public CommonResult<DmsShopAfterSale> auditAfterSale(@PathVariable Long id,
                                                        @Valid @RequestBody ShopAfterSaleAuditDTO dto) {
        return CommonResult.success(afterSaleService.audit(id, dto));
    }

    @Operation(summary = "后台确认收到退货并退款")
    @PutMapping("/admin/after-sales/{id}/return-received")
    public CommonResult<DmsShopAfterSale> confirmReturnReceived(@PathVariable Long id,
                                                                @Valid @RequestBody(required = false) ShopAfterSaleAuditDTO dto) {
        return CommonResult.success(afterSaleService.confirmReturnReceived(id, dto));
    }

    private void applyFrontOrderVisibility(ShopOrderVO vo) {
        if (vo == null) {
            return;
        }
        DmsTenantDisplayConfig config = vo.getDisplayConfig();
        boolean showPv = config == null || !Integer.valueOf(0).equals(config.getShowPv());
        if (!showPv && vo.getItems() != null) {
            vo.getItems().forEach(item -> {
                item.setPvValue(null);
                item.setTotalPv(null);
            });
        }
        // 商品成本、公司利润和整单奖金拨出属于后台账务数据，不向下单会员返回。
        ShopPublicViewSanitizer.order(vo);
    }
}
