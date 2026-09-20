package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsWechatLogisticsFollowTaskDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.WeChatWaybillTokenVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import com.macro.mall.distribution.wechat.WeChatPayGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeChatLogisticsQueryService {

    private final WeChatMiniProgramProperties miniProgramProperties;
    private final WeChatMiniProgramGateway miniProgramGateway;
    private final WeChatPayGateway payGateway;
    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final DmsShopOrderItemDao itemDao;
    private final DmsWechatLogisticsFollowTaskDao followTaskDao;

    @Value("${shop.logistics.fallback-image-url:https://lingqimall.com/favicon.ico}")
    private String fallbackImageUrl;

    @Value("${shop.public-origin:https://lingqimall.com}")
    private String publicOrigin;

    private volatile CompanyCache companyCache;

    public WeChatWaybillTokenVO token(ShopOrderVO detail, Long shipmentId) {
        if (!miniProgramProperties.loginReady()) Asserts.fail("微信物流查询组件尚未配置");
        DmsShopOrder order = detail == null ? null : detail.getOrder();
        if (order == null || order.getId() == null) Asserts.fail("订单不存在");
        if (!"WECHAT".equalsIgnoreCase(order.getPayType()) || order.getPayTime() == null) {
            Asserts.fail("该订单不是微信支付订单，无法使用微信物流查询");
        }
        DmsShopOrderShipment shipment = selectShipment(detail.getShipments(), shipmentId);
        if (shipment == null || shipment.getId() == null || blank(shipment.getDeliveryNo())) {
            Asserts.fail("订单尚未录入有效运单号");
        }
        Long tenantId = TenantContext.getTenantId();
        String token = register(tenantId, order, shipment);
        followTaskDao.completeInteractive(tenantId, order.getId(), shipment.getId(), order.getUserId(),
                digest(tenantId, order, shipment), LocalDateTime.now());
        return new WeChatWaybillTokenVO(shipment.getId(), token);
    }

    /** 后台自动任务与用户主动查询共用同一套身份、支付、承运商和商品核验。 */
    String register(Long tenantId, DmsShopOrder order, DmsShopOrderShipment shipment) {
        if (!miniProgramProperties.loginReady()) Asserts.fail("微信物流查询组件尚未配置");
        if (tenantId == null || order == null || shipment == null) Asserts.fail("订单不存在");
        if (!"WECHAT".equalsIgnoreCase(order.getPayType()) || order.getPayTime() == null) {
            Asserts.fail("该订单不是微信支付订单，无法使用微信物流查询");
        }
        if (blank(shipment.getDeliveryNo())) Asserts.fail("订单尚未录入有效运单号");
        String deliveryId = resolveCompany(shipment.getDeliveryCompany());
        if (deliveryId == null) Asserts.fail("该承运商暂不支持微信物流查询");
        if (blank(order.getReceiverPhone())) Asserts.fail("订单收货手机号不完整，无法查询物流");

        DmsWechatMiniProgramIdentity identity = identityDao.selectByUser(tenantId,
                SecureUtil.sha256(miniProgramProperties.getAppId().trim()), order.getUserId());
        if (identity == null || blank(identity.getOpenId())) Asserts.fail("请先使用微信快捷登录后再查看物流");

        String paymentNo = blank(order.getPaymentOrderNo()) ? order.getOrderNo() : order.getPaymentOrderNo();
        WeChatPayGateway.PaymentResult payment = payGateway.query(paymentNo);
        if (payment == null || !"SUCCESS".equalsIgnoreCase(payment.state())
                || blank(payment.transactionId()) || !identity.getOpenId().equals(payment.openId())) {
            Asserts.fail("暂时无法核对微信支付订单，请稍后重试");
        }

        WeChatMiniProgramGateway.WaybillTrackingResult result = miniProgramGateway.followWaybill(
                new WeChatMiniProgramGateway.WaybillTrackingCommand(identity.getOpenId(), null,
                        order.getReceiverPhone(), deliveryId, shipment.getDeliveryNo(), payment.transactionId(),
                        "pages/order-detail/index?id=" + order.getId(), goods(order.getId())));
        if (result == null || blank(result.waybillToken())) Asserts.fail("微信物流查询暂时不可用，请稍后重试");
        return result.waybillToken();
    }

    private DmsShopOrderShipment selectShipment(List<DmsShopOrderShipment> shipments, Long shipmentId) {
        if (shipments == null || shipments.isEmpty()) return null;
        if (shipmentId == null) return shipments.get(0);
        return shipments.stream().filter(item -> shipmentId.equals(item.getId())).findFirst().orElse(null);
    }

    private List<WeChatMiniProgramGateway.WaybillGoods> goods(Long orderId) {
        List<DmsShopOrderItem> items = itemDao.selectByOrderId(orderId);
        List<WeChatMiniProgramGateway.WaybillGoods> goods = new ArrayList<>();
        if (items != null) {
            for (DmsShopOrderItem item : items) {
                if (goods.size() >= 5) break;
                String name = limit(blank(item.getProductName()) ? "商城商品" : item.getProductName().trim(), 60);
                String description = limit((blank(item.getSkuName()) ? name : item.getSkuName().trim())
                        + " × " + Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity()), 40);
                goods.add(new WeChatMiniProgramGateway.WaybillGoods(name, imageUrl(item.getProductCover()), description));
            }
        }
        if (goods.isEmpty()) goods.add(new WeChatMiniProgramGateway.WaybillGoods("商城商品", imageUrl(null), "商城商品"));
        return List.copyOf(goods);
    }

    private String imageUrl(String value) {
        if (!blank(value) && value.trim().startsWith("https://")) return value.trim();
        if (!blank(value) && value.trim().startsWith("/")) {
            String origin = blank(publicOrigin) ? "https://lingqimall.com" : publicOrigin.trim();
            return origin.replaceAll("/+$", "") + (value.startsWith("/api/") ? value : "/api" + value);
        }
        return blank(fallbackImageUrl) ? "https://lingqimall.com/favicon.ico" : fallbackImageUrl.trim();
    }

    private String resolveCompany(String value) {
        if (blank(value)) return null;
        Map<String, String> companies = deliveryCompanies();
        String direct = companies.get(value.trim().toLowerCase(Locale.ROOT));
        return direct == null ? companies.get(normalizeCompany(value)) : direct;
    }

    private Map<String, String> deliveryCompanies() {
        CompanyCache current = companyCache;
        if (current != null && current.expiresAt().isAfter(Instant.now())) return current.companies();
        synchronized (this) {
            current = companyCache;
            if (current != null && current.expiresAt().isAfter(Instant.now())) return current.companies();
            Map<String, String> rows = new LinkedHashMap<>();
            for (WeChatMiniProgramGateway.DeliveryCompany company : miniProgramGateway.deliveryCompanies()) {
                rows.put(company.id().trim().toLowerCase(Locale.ROOT), company.id().trim());
                rows.put(normalizeCompany(company.name()), company.id().trim());
            }
            current = new CompanyCache(Map.copyOf(rows), Instant.now().plusSeconds(21_600));
            companyCache = current;
            return current.companies();
        }
    }

    private String normalizeCompany(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s()（）]", "")
                .replace("股份有限公司", "").replace("有限公司", "")
                .replace("速运", "").replace("快递", "").replace("物流", "");
    }

    private String limit(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        return count <= maxCodePoints ? value : value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String digest(Long tenantId, DmsShopOrder order, DmsShopOrderShipment shipment) {
        return SecureUtil.sha256(tenantId + "|" + order.getId() + "|" + shipment.getId()
                + "|" + shipment.getDeliveryCompany() + "|" + shipment.getDeliveryNo());
    }

    private record CompanyCache(Map<String, String> companies, Instant expiresAt) {
    }
}
