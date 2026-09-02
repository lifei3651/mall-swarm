package com.macro.mall.distribution.wechat;

import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.config.WithdrawalPayoutProperties;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.service.WithdrawalPayoutGateway;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.cipher.RSAPrivacyEncryptor;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.core.http.UrlEncoder;
import com.wechat.pay.java.core.util.PemUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 微信商家转账：官方 API v3 客户端负责签名与验签，最终查询再核对收款 OpenID 和金额。 */
@Component
@RequiredArgsConstructor
public class WeChatWithdrawalPayoutGateway implements WithdrawalPayoutGateway {
    private static final String BASE_URL = "https://api.mch.weixin.qq.com/v3/fund-app/mch-transfer/transfer-bills";

    private final WithdrawalPayoutProperties payoutProperties;
    private final WeChatPayProperties payProperties;
    private final WeChatMiniProgramProperties miniProgramProperties;
    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Override
    public boolean supports(Integer withdrawType) {
        return Integer.valueOf(2).equals(withdrawType);
    }

    @Override
    public boolean configured() {
        return payoutProperties.wechatReady() && payProperties.isConfigured()
                && miniProgramProperties.loginReady();
    }

    @Override
    public PayoutResult initiate(PayoutCommand command) {
        Recipient recipient;
        HttpClient client;
        HttpHeaders headers = new HttpHeaders();
        JsonRequestBody requestBody;
        try {
            recipient = recipient(command);
            RSAPrivacyEncryptor encryptor = new RSAPrivacyEncryptor(
                    PemUtil.loadPublicKeyFromPath(payProperties.getPublicKeyPath().trim()),
                    payProperties.getPublicKeyId().trim());
            Map<String, Object> body = Map.of(
                    "appid", miniProgramProperties.getAppId().trim(),
                    "out_bill_no", command.requestNo(),
                    "transfer_scene_id", payoutProperties.getWechatTransferSceneId().trim(),
                    "openid", recipient.openId(),
                    "user_name", encryptor.encrypt(command.recipientName()),
                    "transfer_amount", fen(command.amount()),
                    "transfer_remark", "会员奖金提现",
                    "transfer_scene_report_infos", List.of(Map.of(
                            "info_type", payoutProperties.getWechatReportInfoType().trim(),
                            "info_content", payoutProperties.getWechatReportInfoContent().trim())));
            headers.addHeader("Wechatpay-Serial", encryptor.getWechatpaySerial());
            requestBody = new JsonRequestBody.Builder().body(objectMapper.writeValueAsString(body)).build();
            client = client();
        } catch (Exception e) {
            return failedBeforeRequest(command, e);
        }
        try {
            WeChatTransferResponse response = client.post(headers, BASE_URL, requestBody,
                    WeChatTransferResponse.class).getServiceResponse();
            if (response != null && "SUCCESS".equals(upper(response.transferState()))) {
                return query(command, response.transferBillNo());
            }
            return map(command, recipient, response, false);
        } catch (Exception e) {
            return unknown(command, e.getClass().getSimpleName());
        }
    }

    @Override
    public PayoutResult query(PayoutCommand command, String providerOrderNo) {
        try {
            Recipient recipient = recipient(command);
            String url = BASE_URL + "/out-bill-no/" + UrlEncoder.urlEncode(command.requestNo());
            WeChatTransferResponse response = client().get(new HttpHeaders(), url,
                    WeChatTransferResponse.class).getServiceResponse();
            return map(command, recipient, response, true);
        } catch (Exception e) {
            return unknown(command, e.getClass().getSimpleName());
        }
    }

    private PayoutResult map(PayoutCommand command, Recipient recipient,
                             WeChatTransferResponse response, boolean queried) {
        if (response == null) return unknown(command, "EMPTY_RESPONSE");
        String status = upper(response.transferState());
        State state = switch (status) {
            case "SUCCESS" -> State.SUCCESS;
            case "FAIL", "FAILED", "CANCELLED" -> State.FAILED;
            case "WAIT_USER_CONFIRM" -> State.WAIT_USER_CONFIRM;
            case "ACCEPTED", "PROCESSING", "TRANSFERING" -> State.PROCESSING;
            default -> State.UNKNOWN;
        };
        BigDecimal responseAmount = response.transferAmount() == null
                ? (queried ? null : command.amount())
                : BigDecimal.valueOf(response.transferAmount(), 2).setScale(2);
        if (state == State.SUCCESS && (!queried
                || !command.requestNo().equals(response.outBillNo())
                || !miniProgramProperties.getAppId().trim().equals(response.appid())
                || !payProperties.getMchId().trim().equals(response.mchId())
                || !recipient.openId().equals(response.openid())
                || responseAmount == null || responseAmount.compareTo(command.amount()) != 0)) {
            state = State.UNKNOWN;
        }
        String recipientHash = SecureUtil.sha256("WECHAT\0" + miniProgramProperties.getAppId().trim()
                + "\0" + recipient.openId());
        String digest = SecureUtil.sha256(String.join("\0", value(response.outBillNo()),
                value(response.transferBillNo()), status, value(responseAmount), recipientHash));
        return new PayoutResult(state, command.requestNo(), response.transferBillNo(), status,
                responseAmount, recipientHash, digest, safeCode(response.failReason()), response.packageInfo());
    }

    private Recipient recipient(PayoutCommand command) {
        if (!configured()) throw new IllegalStateException("WECHAT_PAYOUT_NOT_CONFIGURED");
        if (command.memberId() == null) throw new IllegalStateException("MEMBER_ID_MISSING");
        String appIdHash = SecureUtil.sha256(miniProgramProperties.getAppId().trim());
        DmsWechatMiniProgramIdentity identity = identityDao.selectByMember(
                TenantContext.getTenantId(), appIdHash, command.memberId());
        if (identity == null || identity.getOpenId() == null || identity.getOpenId().isBlank()
                || !command.userId().equals(identity.getUserId())) {
            throw new IllegalStateException("WECHAT_IDENTITY_NOT_BOUND");
        }
        return new Recipient(identity.getOpenId());
    }

    private HttpClient client() {
        HttpClient value = httpClient;
        if (value != null) return value;
        synchronized (this) {
            if (httpClient == null) {
                Config config = new RSAPublicKeyConfig.Builder()
                        .merchantId(payProperties.getMchId().trim())
                        .privateKeyFromPath(payProperties.getPrivateKeyPath().trim())
                        .merchantSerialNumber(payProperties.getMerchantSerialNumber().trim())
                        .publicKeyFromPath(payProperties.getPublicKeyPath().trim())
                        .publicKeyId(payProperties.getPublicKeyId().trim())
                        .apiV3Key(payProperties.getApiV3Key())
                        .build();
                httpClient = new DefaultHttpClientBuilder().config(config)
                        .connectTimeoutMs(payProperties.safeConnectTimeoutMs())
                        .readTimeoutMs(payProperties.safeReadTimeoutMs())
                        .writeTimeoutMs(payProperties.safeReadTimeoutMs())
                        .build();
            }
            return httpClient;
        }
    }

    private PayoutResult unknown(PayoutCommand command, String failureCode) {
        return new PayoutResult(State.UNKNOWN, command.requestNo(), null, "UNKNOWN", null,
                null, SecureUtil.sha256(command.requestNo() + "\0UNKNOWN"), safeCode(failureCode), null);
    }

    private PayoutResult failedBeforeRequest(PayoutCommand command, Exception error) {
        String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new PayoutResult(State.FAILED, command.requestNo(), null, "LOCAL_VALIDATION_FAILED",
                command.amount(), null, SecureUtil.sha256(command.requestNo() + "\0LOCAL_VALIDATION_FAILED"),
                safeCode(reason), null);
    }

    private long fen(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    private String upper(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeCode(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "");
        return normalized.isBlank() ? "PROVIDER_ERROR" : normalized.substring(0, Math.min(64, normalized.length()));
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record Recipient(String openId) {
    }

    private record WeChatTransferResponse(
            @JsonProperty("out_bill_no") String outBillNo,
            @JsonProperty("transfer_bill_no") String transferBillNo,
            @JsonProperty("appid") String appid,
            @JsonProperty("mch_id") String mchId,
            @JsonProperty("openid") String openid,
            @JsonProperty("transfer_amount") Long transferAmount,
            @JsonProperty("state") String transferState,
            @JsonProperty("fail_reason") String failReason,
            @JsonProperty("package_info") String packageInfo) {
    }
}
