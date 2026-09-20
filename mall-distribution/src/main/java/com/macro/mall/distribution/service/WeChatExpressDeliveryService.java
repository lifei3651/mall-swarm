package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsShopServiceAddressDao;
import com.macro.mall.distribution.dao.DmsWechatExpressOrderDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.WechatExpressOrderDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.entity.DmsWechatExpressOrder;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.WechatExpressOptionsVO;
import com.macro.mall.distribution.vo.WechatExpressShipmentVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeChatExpressDeliveryService {
    private final WeChatMiniProgramProperties miniProgramProperties;
    private final WeChatMiniProgramGateway gateway;
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderItemDao itemDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderShipmentDao shipmentDao;
    private final DmsShopServiceAddressDao serviceAddressDao;
    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final DmsWechatExpressOrderDao expressOrderDao;
    private final DmsMerchantDao merchantDao;
    private final OrderShipmentService orderShipmentService;
    private final WeChatExpressCancellationStateService cancellationStateService;

    @Value("${shop.logistics.fallback-image-url:https://lingqimall.com/favicon.ico}")
    private String fallbackImageUrl;

    @Value("${shop.public-origin:https://lingqimall.com}")
    private String publicOrigin;

    public WechatExpressOptionsVO options() {
        if (!miniProgramProperties.loginReady()) {
            return new WechatExpressOptionsVO(false, "微信小程序服务端凭据尚未配置", List.of());
        }
        Map<String, WeChatMiniProgramGateway.ExpressDeliveryCompany> deliveries = deliveries();
        List<WechatExpressOptionsVO.Account> rows = gateway.expressAccounts().stream().map(account -> {
            WeChatMiniProgramGateway.ExpressDeliveryCompany delivery = deliveries.get(account.deliveryId());
            List<WeChatMiniProgramGateway.ExpressServiceType> services = account.serviceTypes().isEmpty()
                    && delivery != null ? delivery.serviceTypes() : account.serviceTypes();
            return new WechatExpressOptionsVO.Account(account.bizId(), account.deliveryId(),
                    delivery == null ? account.deliveryId() : delivery.deliveryName(), account.alias(),
                    account.statusCode(), account.quota(), services.stream()
                    .map(item -> new WechatExpressOptionsVO.ServiceType(item.serviceType(), item.serviceName()))
                    .toList());
        }).toList();
        boolean configured = rows.stream().anyMatch(item -> item.statusCode() == 0 && !item.serviceTypes().isEmpty());
        String message = configured ? "已读取微信后台绑定的快递账号"
                : "微信后台尚无可用快递账号，请先在物流服务的快递配送中完成配置";
        return new WechatExpressOptionsVO(configured, message, rows);
    }

    public WechatExpressShipmentVO create(Long orderId, WechatExpressOrderDTO dto) {
        if (orderId == null || dto == null) Asserts.fail("快递下单参数不完整");
        if (!miniProgramProperties.loginReady()) Asserts.fail("微信快递配送尚未配置");
        Long tenantId = TenantContext.getTenantId();
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertOrderAccess(order);
        if (!Integer.valueOf(1).equals(order.getStatus()) && !Integer.valueOf(2).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能生成快递运单");
        }
        if (!"WECHAT".equalsIgnoreCase(order.getPayType()) || order.getPayTime() == null) {
            Asserts.fail("只有已完成微信支付的小程序订单才能使用微信快递配送");
        }
        if (afterSaleDao.selectOpenByOrderId(orderId) != null) {
            Asserts.fail("订单正在售后处理中，暂不能生成快递运单");
        }
        int remaining = Math.max(0, itemDao.sumQuantityByOrderId(orderId)
                - afterSaleItemDao.sumApprovedQuantityByOrderId(orderId)
                - shipmentDao.sumQuantityByOrderId(orderId));
        if (dto.getShipmentQuantity() > remaining) {
            Asserts.fail("发货数量超过订单剩余可发件数（剩余 " + remaining + " 件）");
        }

        DmsWechatExpressOrder record = expressOrderDao.selectByRequest(tenantId, orderId, dto.getRequestKey());
        if (record != null && "SUCCESS".equals(record.getStatus())) return result(record, null);

        AccountChoice choice = accountChoice(dto);
        DmsWechatMiniProgramIdentity identity = identityDao.selectByUser(tenantId,
                SecureUtil.sha256(miniProgramProperties.getAppId().trim()), order.getUserId());
        if (identity == null || blank(identity.getOpenId())) Asserts.fail("下单会员未绑定当前微信小程序账号");
        DmsShopServiceAddress sender = serviceAddressDao.selectDefaultForMerchant(
                tenantId, order.getMerchantId(), 1);
        if (sender == null) Asserts.fail("请先在商城设置中配置启用的默认发货地址");
        validateReceiver(order);
        if ("SF".equalsIgnoreCase(choice.account().deliveryId())
                && (dto.getExpectedPickupTime() == null || !dto.getExpectedPickupTime().isAfter(LocalDateTime.now()))) {
            Asserts.fail("顺丰下单必须选择晚于当前时间的上门揽件时间");
        }

        if (record == null) {
            record = newRecord(tenantId, order, dto, choice);
            expressOrderDao.insertIgnore(record);
            record = expressOrderDao.selectByRequest(tenantId, orderId, dto.getRequestKey());
        }
        if (record == null) Asserts.fail("保存微信快递下单请求失败，请稍后重试");
        if (!sameRequest(record, dto, choice)) Asserts.fail("该快递下单请求标识已被使用，请关闭窗口后重新发起");

        try {
            if (blank(record.getWaybillId())) {
                WeChatMiniProgramGateway.ExpressOrderResult created = gateway.createExpressOrder(command(
                        record, order, identity.getOpenId(), sender, dto.getExpectedPickupTime()));
                expressOrderDao.markWaybill(tenantId, record.getId(), created.waybillId());
                record.setWaybillId(created.waybillId());
                record.setStatus("WAYBILL_CREATED");
            }
            ShopOrderShipDTO ship = new ShopOrderShipDTO();
            ship.setDeliveryCompany(record.getDeliveryName());
            ship.setDeliveryNo(record.getWaybillId());
            ship.setShipmentQuantity(record.getShipmentQuantity());
            orderShipmentService.shipWechatExpressOrder(orderId, ship);
            DmsShopOrderShipment shipment = shipmentDao.selectByOrderAndTracking(orderId,
                    record.getDeliveryName(), record.getWaybillId());
            if (shipment == null) throw new ApiException("运单已生成，但商城包裹保存失败，请使用同一窗口重试");
            expressOrderDao.markSuccess(tenantId, record.getId(), shipment.getId());
            record.setShipmentId(shipment.getId());
            record.setStatus("SUCCESS");
            String printHtml = null;
            try {
                WeChatMiniProgramGateway.ExpressOrderResult printable = gateway.getExpressOrder(
                        new WeChatMiniProgramGateway.ExpressOrderLookup(record.getExpressOrderNo(),
                                identity.getOpenId(), record.getDeliveryId(), record.getWaybillId(), 1));
                printHtml = printable == null ? null : printable.printHtml();
            } catch (RuntimeException ignored) {
                // 面单下载失败不回滚已经完成的真实运单和商城发货；管理员仍可稍后从微信后台打印。
            }
            return result(record, printHtml);
        } catch (RuntimeException exception) {
            expressOrderDao.markFailed(tenantId, record.getId(), exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public boolean cancel(Long orderId, Long shipmentId) {
        WeChatExpressCancellationStateService.CancellationContext context =
                cancellationStateService.prepare(orderId, shipmentId);
        if (context.alreadyCancelled()) return true;
        if (!context.cancelConfirmed()) {
            DmsWechatMiniProgramIdentity identity = identityDao.selectByUser(context.tenantId(),
                    SecureUtil.sha256(miniProgramProperties.getAppId().trim()), context.userId());
            if (identity == null || blank(identity.getOpenId())) {
                cancellationStateService.restore(context, "WECHAT_IDENTITY_MISSING");
                Asserts.fail("下单会员未绑定当前微信小程序账号，无法撤销微信运单");
            }
            try {
                gateway.cancelExpressOrder(new WeChatMiniProgramGateway.ExpressOrderLookup(
                        context.expressOrderNo(), identity.getOpenId(), context.deliveryId(),
                        context.waybillId(), 1));
            } catch (RuntimeException exception) {
                cancellationStateService.restore(context, exception.getClass().getSimpleName());
                throw exception;
            }
            // 微信已经确认撤单后，即使本地确认暂时失败也不能恢复成 SUCCESS，否则会把已取消的真实运单误报为有效。
            cancellationStateService.confirm(context);
        }
        return cancellationStateService.finish(context);
    }

    private AccountChoice accountChoice(WechatExpressOrderDTO dto) {
        WeChatMiniProgramGateway.ExpressAccount account = gateway.expressAccounts().stream()
                .filter(item -> item.statusCode() == 0
                        && item.deliveryId().equals(dto.getDeliveryId()) && item.bizId().equals(dto.getBizId()))
                .findFirst().orElseThrow(() -> new ApiException("所选微信快递账号不可用，请刷新后重试"));
        WeChatMiniProgramGateway.ExpressDeliveryCompany delivery = deliveries().get(account.deliveryId());
        String deliveryName = delivery == null ? account.deliveryId() : delivery.deliveryName();
        List<WeChatMiniProgramGateway.ExpressServiceType> services = account.serviceTypes().isEmpty()
                && delivery != null ? delivery.serviceTypes() : account.serviceTypes();
        WeChatMiniProgramGateway.ExpressServiceType service = services.stream()
                .filter(item -> item.serviceType() == dto.getServiceType())
                .findFirst().orElseThrow(() -> new ApiException("所选快递服务类型不可用，请刷新后重试"));
        return new AccountChoice(account, service, deliveryName);
    }

    private DmsWechatExpressOrder newRecord(Long tenantId, DmsShopOrder order, WechatExpressOrderDTO dto,
                                             AccountChoice choice) {
        DmsWechatExpressOrder value = new DmsWechatExpressOrder();
        value.setTenantId(tenantId);
        value.setOrderId(order.getId());
        value.setUserId(order.getUserId());
        value.setRequestKey(dto.getRequestKey());
        value.setExpressOrderNo("LQ-" + order.getOrderNo() + "-"
                + SecureUtil.sha256(dto.getRequestKey()).substring(0, 12).toUpperCase(Locale.ROOT));
        value.setDeliveryId(choice.account().deliveryId());
        value.setDeliveryName(limit(choice.deliveryName(), 50));
        value.setBizId(choice.account().bizId());
        value.setServiceType(choice.service().serviceType());
        value.setServiceName(choice.service().serviceName());
        value.setShipmentQuantity(dto.getShipmentQuantity());
        value.setPackageCount(dto.getPackageCount());
        value.setWeight(dto.getWeight());
        value.setPackageLength(dto.getPackageLength());
        value.setPackageWidth(dto.getPackageWidth());
        value.setPackageHeight(dto.getPackageHeight());
        value.setRemark(trim(dto.getRemark()));
        value.setStatus("PENDING");
        return value;
    }

    private WeChatMiniProgramGateway.ExpressOrderCommand command(DmsWechatExpressOrder record,
                                                                  DmsShopOrder order, String openId,
                                                                  DmsShopServiceAddress sender,
                                                                  LocalDateTime expectedPickupTime) {
        List<DmsShopOrderItem> items = itemDao.selectByOrderId(order.getId());
        List<WeChatMiniProgramGateway.ExpressCargoItem> cargo = new ArrayList<>();
        List<WeChatMiniProgramGateway.ExpressShopItem> shop = new ArrayList<>();
        for (DmsShopOrderItem item : items == null ? List.<DmsShopOrderItem>of() : items) {
            if (cargo.size() < 20) cargo.add(new WeChatMiniProgramGateway.ExpressCargoItem(
                    limit(blank(item.getProductName()) ? "商城商品" : item.getProductName(), 60),
                    Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity())));
            if (shop.size() < 5) shop.add(new WeChatMiniProgramGateway.ExpressShopItem(
                    limit(blank(item.getProductName()) ? "商城商品" : item.getProductName(), 60),
                    imageUrl(item.getProductCover()), limit(blank(item.getSkuName()) ? "默认规格" : item.getSkuName(), 40)));
        }
        if (cargo.isEmpty()) cargo.add(new WeChatMiniProgramGateway.ExpressCargoItem("商城商品", record.getShipmentQuantity()));
        if (shop.isEmpty()) shop.add(new WeChatMiniProgramGateway.ExpressShopItem("商城商品", imageUrl(null), "默认规格"));
        Long pickup = expectedPickupTime == null ? null
                : expectedPickupTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return new WeChatMiniProgramGateway.ExpressOrderCommand(record.getExpressOrderNo(), openId,
                record.getDeliveryId(), record.getBizId(), record.getRemark(),
                address(sender.getContactName(), sender.getContactPhone(), sender.getMerchantName(), sender.getProvince(),
                        sender.getCity(), sender.getDistrict(), sender.getDetailAddress()),
                address(order.getReceiverName(), order.getReceiverPhone(), null, order.getReceiverProvince(),
                        order.getReceiverCity(), order.getReceiverDistrict(), order.getReceiverDetailAddress()),
                record.getPackageCount(), record.getWeight().doubleValue(), record.getPackageLength().doubleValue(),
                record.getPackageWidth().doubleValue(), record.getPackageHeight().doubleValue(), List.copyOf(cargo),
                "pages/order-detail/index?id=" + order.getId(), List.copyOf(shop), record.getServiceType(),
                record.getServiceName(), pickup);
    }

    private WeChatMiniProgramGateway.ExpressAddress address(String name, String phone, String company,
                                                             String province, String city, String district,
                                                             String detail) {
        return new WeChatMiniProgramGateway.ExpressAddress(name, phone, company, province, city, district, detail);
    }

    private void assertOrderAccess(DmsShopOrder order) {
        Long orderTenant = order.getTenantId() == null ? 1L : order.getTenantId();
        if (!TenantContext.getTenantId().equals(orderTenant)) Asserts.fail("无权访问当前租户数据");
        DmsAdminUser admin = AdminContext.get();
        if (admin == null || admin.getMerchantId() == null) return;
        if (!admin.getMerchantId().equals(order.getMerchantId())) Asserts.fail("不能处理其他商户的订单");
        DmsMerchant merchant = merchantDao.selectById(admin.getMerchantId());
        if (merchant == null || !"ENABLED".equals(merchant.getFulfillmentStatus())) {
            Asserts.fail("商户履约权限已由平台接管或冻结，不能发货");
        }
    }

    private void validateReceiver(DmsShopOrder order) {
        if (blank(order.getReceiverName()) || blank(order.getReceiverPhone())
                || blank(order.getReceiverProvince()) || blank(order.getReceiverCity())
                || blank(order.getReceiverDistrict()) || blank(order.getReceiverDetailAddress())) {
            Asserts.fail("订单收货人或结构化地址不完整，无法生成微信快递运单");
        }
    }

    private boolean sameRequest(DmsWechatExpressOrder record, WechatExpressOrderDTO dto, AccountChoice choice) {
        return record.getDeliveryId().equals(choice.account().deliveryId())
                && record.getBizId().equals(choice.account().bizId())
                && record.getServiceType().equals(choice.service().serviceType())
                && record.getShipmentQuantity().equals(dto.getShipmentQuantity())
                && record.getPackageCount().equals(dto.getPackageCount())
                && record.getWeight().compareTo(dto.getWeight()) == 0
                && record.getPackageLength().compareTo(dto.getPackageLength()) == 0
                && record.getPackageWidth().compareTo(dto.getPackageWidth()) == 0
                && record.getPackageHeight().compareTo(dto.getPackageHeight()) == 0;
    }

    private Map<String, WeChatMiniProgramGateway.ExpressDeliveryCompany> deliveries() {
        Map<String, WeChatMiniProgramGateway.ExpressDeliveryCompany> names = new LinkedHashMap<>();
        for (WeChatMiniProgramGateway.ExpressDeliveryCompany item : gateway.expressDeliveryCompanies()) {
            names.put(item.deliveryId(), item);
        }
        return names;
    }

    private WechatExpressShipmentVO result(DmsWechatExpressOrder record, String printHtml) {
        return new WechatExpressShipmentVO(record.getId(), record.getShipmentId(), record.getDeliveryName(),
                record.getWaybillId(), record.getStatus(), printHtml);
    }

    private String imageUrl(String value) {
        if (!blank(value) && value.trim().startsWith("https://")) return value.trim();
        if (!blank(value) && value.trim().startsWith("/")) {
            String origin = blank(publicOrigin) ? "https://lingqimall.com" : publicOrigin.trim();
            return origin.replaceAll("/+$", "") + (value.startsWith("/api/") ? value : "/api" + value);
        }
        return blank(fallbackImageUrl) ? "https://lingqimall.com/favicon.ico" : fallbackImageUrl.trim();
    }

    private String limit(String value, int maxCodePoints) {
        String safe = blank(value) ? "" : value.trim();
        int count = safe.codePointCount(0, safe.length());
        return count <= maxCodePoints ? safe : safe.substring(0, safe.offsetByCodePoints(0, maxCodePoints));
    }

    private String trim(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private record AccountChoice(WeChatMiniProgramGateway.ExpressAccount account,
                                 WeChatMiniProgramGateway.ExpressServiceType service,
                                 String deliveryName) { }
}
