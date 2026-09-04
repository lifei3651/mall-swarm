package com.macro.mall.distribution.wechat;

import java.util.List;
import java.util.Map;

public interface WeChatMiniProgramGateway {

    LoginIdentity exchangeLoginCode(String code);

    PhoneNumber exchangePhoneCode(String code);

    SubscribeMessageResult sendSubscribeMessage(SubscribeMessageCommand command);

    ShippingInfoResult uploadShippingInfo(ShippingInfoCommand command);

    List<DeliveryCompany> deliveryCompanies();

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
}
