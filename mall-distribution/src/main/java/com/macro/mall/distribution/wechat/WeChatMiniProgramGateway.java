package com.macro.mall.distribution.wechat;

import java.util.List;
import java.util.Map;

public interface WeChatMiniProgramGateway {

    LoginIdentity exchangeLoginCode(String code);

    PhoneNumber exchangePhoneCode(String code);

    SubscribeMessageResult sendSubscribeMessage(SubscribeMessageCommand command);

    ShippingInfoResult uploadShippingInfo(ShippingInfoCommand command);

    List<DeliveryCompany> deliveryCompanies();

    WaybillTrackingResult followWaybill(WaybillTrackingCommand command);

    /** 微信物流助手中已经绑定并可用于电子面单下单的快递账号。 */
    List<ExpressAccount> expressAccounts();

    List<ExpressDeliveryCompany> expressDeliveryCompanies();

    /** 通过微信物流助手生成真实运单。 */
    ExpressOrderResult createExpressOrder(ExpressOrderCommand command);

    /** 获取微信物流助手中的运单，供幂等恢复和电子面单打印使用。 */
    ExpressOrderResult getExpressOrder(ExpressOrderLookup command);

    long getExpressQuota(String deliveryId, String bizId);

    void cancelExpressOrder(ExpressOrderLookup command);

    /** 查询由微信物流助手生成的运单轨迹。 */
    ExpressPathResult getExpressPath(String openId, String deliveryId, String waybillId);

    record LoginIdentity(String openId, String unionId) {
    }

    record PhoneNumber(String phoneNumber, String countryCode) {
    }

    record SubscribeMessageCommand(String openId, String templateId, String page,
                                   String miniProgramState, Map<String, String> data) {
    }

    record SubscribeMessageResult(int errorCode) {
        public boolean success() { return errorCode == 0; }
    }

    record ShippingItem(String trackingNo, String expressCompany, String itemDescription,
                        String receiverContact) {
    }

    record ShippingInfoCommand(String merchantId, String paymentOrderNo, String openId,
                               boolean allDelivered, List<ShippingItem> shipments) {
    }

    record ShippingInfoResult(int errorCode) {
        public boolean success() { return errorCode == 0 || errorCode == 10060023; }
    }

    record DeliveryCompany(String id, String name) {
    }

    record WaybillGoods(String name, String imageUrl, String description) {
    }

    record WaybillTrackingCommand(String openId, String senderPhone, String receiverPhone,
                                  String deliveryId, String waybillId, String transactionId,
                                  String orderDetailPath, List<WaybillGoods> goods) {
    }

    record WaybillTrackingResult(String waybillToken) {
    }

    record ExpressServiceType(int serviceType, String serviceName) {
    }

    record ExpressAccount(String bizId, String deliveryId, String alias, int statusCode,
                          long quota, List<ExpressServiceType> serviceTypes) {
    }

    record ExpressDeliveryCompany(String deliveryId, String deliveryName, boolean cashAvailable,
                                  String cashBizId, List<ExpressServiceType> serviceTypes) {
    }

    record ExpressAddress(String name, String phone, String company, String province,
                          String city, String area, String address) {
    }

    record ExpressCargoItem(String name, int count) {
    }

    record ExpressShopItem(String name, String imageUrl, String description) {
    }

    record ExpressOrderCommand(String orderId, String openId, String deliveryId, String bizId,
                               String remark, ExpressAddress sender, ExpressAddress receiver,
                               int packageCount, double weight, double length, double width,
                               double height, List<ExpressCargoItem> cargoItems, String orderDetailPath,
                               List<ExpressShopItem> shopItems, int serviceType, String serviceName,
                               Long expectedPickupTime) {
    }

    record ExpressOrderLookup(String orderId, String openId, String deliveryId, String waybillId,
                              Integer printType) {
    }

    record ExpressOrderResult(String orderId, String deliveryId, String waybillId,
                              Integer orderStatus, String printHtml) {
    }

    record ExpressPathItem(long actionTime, int actionType, String actionMessage) {
    }

    record ExpressPathResult(List<ExpressPathItem> items) {
    }
}
