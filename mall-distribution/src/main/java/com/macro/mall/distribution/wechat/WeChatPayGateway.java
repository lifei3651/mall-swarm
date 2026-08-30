package com.macro.mall.distribution.wechat;

public interface WeChatPayGateway {

    PrepayResult prepay(PrepayCommand command);

    PaymentResult query(String paymentNo);

    void close(String paymentNo);

    PaymentResult parsePaymentNotification(NotificationRequest request);

    RefundResult refund(RefundCommand command);

    RefundNotification parseRefundNotification(NotificationRequest request);

    record PrepayCommand(String paymentNo, int totalFen, String description, String openId) {
    }

    record PrepayResult(String appId, String timeStamp, String nonceStr, String packageValue,
                        String signType, String paySign) {
    }

    record PaymentResult(String state, String appId, String mchId, String paymentNo,
                         Integer totalFen, String currency, String openId) {
    }

    record RefundCommand(String paymentNo, String refundNo, long refundFen, long totalFen, String reason) {
    }

    record RefundResult(String state, String paymentNo, String refundNo, Long refundFen, Long totalFen,
                        String currency) {
    }

    record RefundNotification(String state, String paymentNo, String refundNo, Long refundFen, Long totalFen,
                              String currency) {
    }

    record NotificationRequest(String serialNumber, String signature, String timestamp, String nonce,
                               String signatureType, String body) {
    }
}
