package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
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
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import com.macro.mall.distribution.vo.ShopProfileVO;
import com.macro.mall.distribution.vo.FreightQuoteVO;

import java.util.List;
import java.util.Map;

public interface ShopService {

    ShopHomeVO getHome(Long tenantId);

    List<DmsShopProduct> listProducts(Long tenantId, String keyword, String categoryName, Integer status);

    List<String> listCategories(Long tenantId);

    List<DmsShopCategory> listFrontCategories(Long tenantId);

    List<DmsShopCategory> listAdminCategories(Long tenantId, Integer status);

    DmsShopCategory saveCategory(DmsShopCategory category);

    DmsShopCategory updateCategory(Long id, DmsShopCategory category);

    boolean updateCategoryStatus(Long id, Integer status);

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

    DmsShopProduct getProduct(Long id);

    ShopProductDetailVO getProductDetail(Long id);

    DmsShopProduct saveProduct(DmsShopProduct product);

    DmsShopProduct updateProduct(Long id, DmsShopProduct product);

    DmsShopProduct publishProduct(Long id, ProductPublishDTO dto);

    boolean updateProductStatus(Long id, Integer status);

    List<DmsShopSku> listSkus(Long productId, Integer status);

    DmsShopSku saveSku(ShopSkuDTO dto);

    DmsShopSku updateSku(Long id, ShopSkuDTO dto);

    boolean updateSkuStatus(Long id, Integer status);

    List<DmsFreightTemplate> listFreightTemplates(Long tenantId, Integer status);

    DmsFreightTemplate saveFreightTemplate(FreightTemplateSaveDTO dto);

    DmsFreightTemplate updateFreightTemplate(Long id, FreightTemplateSaveDTO dto);

    FreightQuoteVO quoteFreight(ShopOrderSubmitDTO dto, DmsShopMember member);

    ShopOrderVO submitOrder(ShopOrderSubmitDTO dto);

    ShopOrderVO submitOrder(ShopOrderSubmitDTO dto, DmsShopMember member);

    ShopOrderVO getOrder(Long orderId);

    List<ShopOrderVO> listOrders(Long userId, Long agentId);

    List<ShopOrderVO> listAdminOrders(String keyword, Integer status, String orderState);

    ShopOrderVO markOrderPaid(Long orderId, String payType);

    boolean cancelOrder(Long orderId, DmsShopMember member);

    int closeExpiredPendingOrders(int limit);

    boolean shipOrder(Long orderId, ShopOrderShipDTO dto);

    boolean confirmReceive(Long orderId, DmsShopMember member);

    ShopProfileVO getProfile(Long userId, Long agentId);

    ShopProfileVO getProfile(DmsShopMember member, Long agentId);

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
