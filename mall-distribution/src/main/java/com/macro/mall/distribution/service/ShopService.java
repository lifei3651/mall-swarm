package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
import com.macro.mall.distribution.dto.ProductNewArrivalDTO;
import com.macro.mall.distribution.dto.FreightTemplateSaveDTO;
import com.macro.mall.distribution.entity.DmsShopBanner;
import com.macro.mall.distribution.entity.DmsShopCategory;
import com.macro.mall.distribution.entity.DmsShopNotice;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsFreightTemplate;
import com.macro.mall.distribution.vo.ShopHomeVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
import com.macro.mall.distribution.vo.ShopTradeDetailVO;
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import com.macro.mall.distribution.vo.ShopProfileVO;
import com.macro.mall.distribution.vo.FreightQuoteVO;
import com.macro.mall.distribution.vo.PurchaseLimitCheckVO;
import com.macro.mall.distribution.vo.ShopBusinessConfigVO;
import com.macro.mall.distribution.vo.ShopBrandCultureVO;

import java.util.List;
import java.util.Map;

public interface ShopService {

    ShopHomeVO getHome(Long tenantId);

    List<DmsShopProduct> listProducts(Long tenantId, String keyword, String categoryName, Integer status, String stockStatus);

    List<DmsShopProduct> listNewArrivals(Long tenantId, Integer limit);

    ShopBrandCultureVO getBrandCulture(Long tenantId);

    CommonPage<DmsShopProduct> listProductPage(Long tenantId, String keyword, String categoryName,
                                               Integer status, String stockStatus,
                                               Integer pageNum, Integer pageSize);

    CommonPage<DmsShopProduct> listAdminProductPage(Long tenantId, String keyword, String categoryName,
                                                    Integer status, String stockStatus,
                                                    Integer pageNum, Integer pageSize);

    ShopBusinessConfigVO getBusinessConfig(DmsShopMember member);

    List<DmsShopProduct> listRepurchaseProducts(String keyword, DmsShopMember member);

    ShopProductDetailVO getRepurchaseProductDetail(Long id, DmsShopMember member);

    List<String> listCategories(Long tenantId);

    List<DmsShopCategory> listFrontCategories(Long tenantId);

    List<DmsShopCategory> listAdminCategories(Long tenantId, Integer status);

    DmsShopCategory saveCategory(DmsShopCategory category);

    DmsShopCategory updateCategory(Long id, DmsShopCategory category);

    boolean deleteCategory(Long id);

    boolean updateCategoryStatus(Long id, Integer status);

    boolean updateCategoryShowOnHome(Long id, Integer showOnHome);

    List<DmsShopBanner> listAdminBanners(Long tenantId, Integer status);

    DmsShopBanner saveBanner(DmsShopBanner banner);

    DmsShopBanner updateBanner(Long id, DmsShopBanner banner);

    boolean updateBannerStatus(Long id, Integer status);

    List<DmsShopNotice> listAdminNotices(Long tenantId, Integer status);

    List<DmsShopNotice> listActiveNotices(Long tenantId);

    DmsShopNotice getNotice(Long id);

    DmsShopNotice saveNotice(DmsShopNotice notice);

    DmsShopNotice updateNotice(Long id, DmsShopNotice notice);

    boolean updateNoticeStatus(Long id, Integer status);

    boolean deleteNotice(Long id);

    DmsShopProduct getProduct(Long id);

    ShopProductDetailVO getProductDetail(Long id);

    DmsShopProduct saveProduct(DmsShopProduct product);

    DmsShopProduct updateProduct(Long id, DmsShopProduct product);

    DmsShopProduct publishProduct(Long id, ProductPublishDTO dto);

    boolean updateProductStatus(Long id, Integer status);

    DmsShopProduct updateProductNewArrival(Long id, ProductNewArrivalDTO dto);

    List<DmsShopSku> listSkus(Long productId, Integer status);

    List<DmsShopSku> listAdminSkus(Long productId, Integer status);

    DmsShopSku saveSku(ShopSkuDTO dto);

    DmsShopSku updateSku(Long id, ShopSkuDTO dto);

    boolean updateSkuStatus(Long id, Integer status);

    List<DmsFreightTemplate> listFreightTemplates(Long tenantId, Integer status);

    DmsFreightTemplate saveFreightTemplate(FreightTemplateSaveDTO dto);

    DmsFreightTemplate updateFreightTemplate(Long id, FreightTemplateSaveDTO dto);

    FreightQuoteVO quoteFreight(ShopOrderSubmitDTO dto, DmsShopMember member);

    PurchaseLimitCheckVO checkPurchaseLimit(Long productId, Integer quantity, DmsShopMember member);

    ShopOrderVO submitOrder(ShopOrderSubmitDTO dto);

    ShopOrderVO submitOrder(ShopOrderSubmitDTO dto, DmsShopMember member);

    /** 仅供秒杀资格与活动库存已原子占用后的内部下单链路调用。 */
    ShopOrderVO submitReservedFlashSaleOrder(ShopOrderSubmitDTO dto, DmsShopMember member);

    ShopOrderVO getOrder(Long orderId);

    List<ShopOrderVO> listOrders(Long userId, Long agentId);

    List<ShopOrderVO> listOrders(Long userId, Long agentId, String orderState);

    List<ShopOrderVO> listAdminOrders(String keyword, Integer status, String orderState);

    ShopOrderStatusSummaryVO getAdminOrderWorkSummary();

    /** 仅平台后台查看联合支付父交易及全部商户子订单。 */
    ShopTradeDetailVO getAdminTrade(Long tradeId);

    ShopOrderVO markOrderPaid(Long orderId, String payType);

    /** 一次性支付交易父单，事务内完成全部商户子单入账。 */
    ShopOrderVO markCheckoutPaid(Long checkoutId, String payType);

    /** 取消尚未支付的父交易及其全部子单。 */
    boolean cancelCheckout(Long checkoutId, DmsShopMember member);

    boolean cancelOrder(Long orderId, DmsShopMember member);

    int closeExpiredPendingOrders(int limit);

    boolean shipOrder(Long orderId, ShopOrderShipDTO dto);

    boolean updateOrderServiceRemark(Long orderId, String serviceRemark);

    boolean confirmReceive(Long orderId, DmsShopMember member);

    ShopProfileVO getProfile(Long userId, Long agentId);

    ShopProfileVO getProfile(DmsShopMember member, Long agentId);

    ShopProfileVO getProfilePerformance(DmsShopMember member);

    ShopOrderStatusSummaryVO getOrderStatusSummary(DmsShopMember member);

    ShopProfileVO getAdminProfile(DmsShopMember member);

    /**
     * 根据用户ID查询代理ID（如果该用户是代理人）
     */
    Long resolveAgentId(Long userId);

    /**
     * 获取当前会员的邀请信息
     */
    java.util.Map<String, Object> getInviteInfo(DmsShopMember member);

    /** 获取注册页展示所需的脱敏邀请人信息。 */
    Map<String, Object> getInviterPreview(String inviteCode);
}
