package com.macro.mall.distribution.wechat;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class OfficialWeChatPayGateway implements WeChatPayGateway {

    private final WeChatPayProperties payProperties;
    private final WeChatMiniProgramProperties miniProgramProperties;
    private volatile ClientBundle clients;

    @Override
    public PrepayResult prepay(PrepayCommand command) {
        Amount amount = new Amount();
        amount.setTotal(command.totalFen());
        amount.setCurrency("CNY");
        Payer payer = new Payer();
        payer.setOpenid(command.openId());
        PrepayRequest request = new PrepayRequest();
        request.setAppid(miniProgramProperties.getAppId().trim());
        request.setMchid(payProperties.getMchId().trim());
        request.setDescription(command.description());
        request.setOutTradeNo(command.paymentNo());
        request.setTimeExpire(OffsetDateTime.now(ZoneOffset.ofHours(8)).plusMinutes(30)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        request.setNotifyUrl(payProperties.getNotifyUrl().trim());
        request.setAmount(amount);
        request.setPayer(payer);
        PrepayWithRequestPaymentResponse response = bundle().payments().prepayWithRequestPayment(request);
        return new PrepayResult(response.getAppId(), response.getTimeStamp(), response.getNonceStr(),
                response.getPackageVal(), response.getSignType(), response.getPaySign());
    }

    @Override
    public PaymentResult query(String paymentNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(payProperties.getMchId().trim());
        request.setOutTradeNo(paymentNo);
        return payment(bundle().payments().queryOrderByOutTradeNo(request));
    }

    @Override
    public void close(String paymentNo) {
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(payProperties.getMchId().trim());
        request.setOutTradeNo(paymentNo);
        bundle().payments().closeOrder(request);
    }

    @Override
    public PaymentResult parsePaymentNotification(NotificationRequest request) {
        return payment(bundle().parser().parse(notificationRequest(request), Transaction.class));
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        AmountReq amount = new AmountReq();
        amount.setRefund(command.refundFen());
        amount.setTotal(command.totalFen());
        amount.setCurrency("CNY");
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(command.paymentNo());
        request.setOutRefundNo(command.refundNo());
        request.setReason(command.reason());
        request.setNotifyUrl(payProperties.getRefundNotifyUrl().trim());
        request.setAmount(amount);
        return refund(bundle().refunds().create(request));
    }

    @Override
    public WeChatPayGateway.RefundNotification parseRefundNotification(NotificationRequest request) {
        com.wechat.pay.java.service.refund.model.RefundNotification notification = bundle().parser().parse(
                notificationRequest(request), com.wechat.pay.java.service.refund.model.RefundNotification.class);
        return new WeChatPayGateway.RefundNotification(
                notification.getRefundStatus() == null ? null : notification.getRefundStatus().name(),
                notification.getOutTradeNo(), notification.getOutRefundNo(),
                notification.getAmount() == null ? null : notification.getAmount().getRefund(),
                notification.getAmount() == null ? null : notification.getAmount().getTotal(),
                notification.getAmount() == null ? null : notification.getAmount().getCurrency());
    }

    private PaymentResult payment(Transaction transaction) {
        return new PaymentResult(transaction.getTradeState() == null ? null : transaction.getTradeState().name(),
                transaction.getAppid(), transaction.getMchid(), transaction.getOutTradeNo(),
                transaction.getAmount() == null ? null : transaction.getAmount().getTotal(),
                transaction.getAmount() == null ? null : transaction.getAmount().getCurrency(),
                transaction.getPayer() == null ? null : transaction.getPayer().getOpenid());
    }

    private RefundResult refund(Refund refund) {
        return new RefundResult(refund.getStatus() == null ? null : refund.getStatus().name(),
                refund.getOutTradeNo(), refund.getOutRefundNo(),
                refund.getAmount() == null ? null : refund.getAmount().getRefund(),
                refund.getAmount() == null ? null : refund.getAmount().getTotal(),
                refund.getAmount() == null ? null : refund.getAmount().getCurrency());
    }

    private RequestParam notificationRequest(NotificationRequest request) {
        return new RequestParam.Builder()
                .serialNumber(request.serialNumber())
                .signature(request.signature())
                .timestamp(request.timestamp())
                .nonce(request.nonce())
                .signType(request.signatureType())
                .body(request.body())
                .build();
    }

    private ClientBundle bundle() {
        if (!payProperties.isConfigured() || !miniProgramProperties.loginReady()) {
            Asserts.fail("微信支付未配置，请联系管理员");
        }
        ClientBundle value = clients;
        if (value != null) return value;
        synchronized (this) {
            if (clients == null) clients = buildClients();
            return clients;
        }
    }

    private ClientBundle buildClients() {
        Config config = new RSAPublicKeyConfig.Builder()
                .merchantId(payProperties.getMchId().trim())
                .privateKeyFromPath(payProperties.getPrivateKeyPath().trim())
                .merchantSerialNumber(payProperties.getMerchantSerialNumber().trim())
                .publicKeyFromPath(payProperties.getPublicKeyPath().trim())
                .publicKeyId(payProperties.getPublicKeyId().trim())
                .apiV3Key(payProperties.getApiV3Key())
                .build();
        HttpClient client = new DefaultHttpClientBuilder().config(config)
                .connectTimeoutMs(payProperties.safeConnectTimeoutMs())
                .readTimeoutMs(payProperties.safeReadTimeoutMs())
                .writeTimeoutMs(payProperties.safeReadTimeoutMs())
                .build();
        return new ClientBundle(
                new JsapiServiceExtension.Builder().httpClient(client).config(config).build(),
                new RefundService.Builder().httpClient(client).config(config).build(),
                new NotificationParser((com.wechat.pay.java.core.notification.NotificationConfig) config));
    }

    private record ClientBundle(JsapiServiceExtension payments, RefundService refunds, NotificationParser parser) {
    }
}
