package com.macro.mall.distribution.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayFundTransCommonQueryModel;
import com.alipay.api.domain.AlipayFundTransUniTransferModel;
import com.alipay.api.domain.Participant;
import com.alipay.api.request.AlipayFundTransCommonQueryRequest;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.response.AlipayFundTransCommonQueryResponse;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.config.WithdrawalPayoutProperties;
import com.macro.mall.distribution.service.WithdrawalPayoutGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/** 支付宝单笔转账：使用官方 SDK 签名验签，并在最终成功前核对业务单号与金额。 */
@Component
@RequiredArgsConstructor
public class AlipayWithdrawalPayoutGateway implements WithdrawalPayoutGateway {
    private static final String PRODUCT_CODE = "TRANS_ACCOUNT_NO_PWD";
    private static final String BIZ_SCENE = "DIRECT_TRANSFER";

    private final WithdrawalPayoutProperties payoutProperties;
    private final AlipayConfig alipayConfig;

    @Override
    public boolean supports(Integer withdrawType) {
        return Integer.valueOf(3).equals(withdrawType);
    }

    @Override
    public boolean configured() {
        return payoutProperties.alipayReady() && alipayConfig.isConfigured();
    }

    @Override
    public PayoutResult initiate(PayoutCommand command) {
        try {
            AlipayFundTransUniTransferModel model = new AlipayFundTransUniTransferModel();
            model.setOutBizNo(command.requestNo());
            model.setProductCode(PRODUCT_CODE);
            model.setBizScene(BIZ_SCENE);
            model.setOrderTitle("会员奖金提现");
            model.setRemark("提现单" + command.requestNo());
            model.setTransAmount(money(command.amount()));
            Participant payee = new Participant();
            payee.setIdentityType("ALIPAY_LOGON_ID");
            payee.setIdentity(command.recipientAccount());
            payee.setName(command.recipientName());
            model.setPayeeInfo(payee);

            AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
            request.setBizModel(model);
            AlipayFundTransUniTransferResponse response = client().execute(request);
            if (!response.isSuccess()) {
                return unknown(command, response.getSubCode());
            }
            if ("SUCCESS".equals(upper(response.getStatus()))) {
                return query(command, response.getOrderId());
            }
            return mapInitiate(command, response);
        } catch (Exception e) {
            return unknown(command, e.getClass().getSimpleName());
        }
    }

    @Override
    public PayoutResult query(PayoutCommand command, String providerOrderNo) {
        try {
            AlipayFundTransCommonQueryModel model = new AlipayFundTransCommonQueryModel();
            model.setProductCode(PRODUCT_CODE);
            model.setBizScene(BIZ_SCENE);
            model.setOutBizNo(command.requestNo());
            if (providerOrderNo != null && !providerOrderNo.isBlank()) model.setOrderId(providerOrderNo);
            AlipayFundTransCommonQueryRequest request = new AlipayFundTransCommonQueryRequest();
            request.setBizModel(model);
            AlipayFundTransCommonQueryResponse response = client().execute(request);
            if (!response.isSuccess()) return unknown(command, response.getSubCode());
            return mapQuery(command, response);
        } catch (Exception e) {
            return unknown(command, e.getClass().getSimpleName());
        }
    }

    private PayoutResult mapInitiate(PayoutCommand command, AlipayFundTransUniTransferResponse response) {
        String status = upper(response.getStatus());
        State state = state(status);
        BigDecimal amount = parseMoney(response.getAmount());
        if (state == State.SUCCESS && (!command.requestNo().equals(response.getOutBizNo())
                || amount == null || amount.compareTo(command.amount()) != 0)) {
            state = State.UNKNOWN;
        }
        return result(command, state, response.getOrderId(), status, amount,
                response.getSubStatus(), response.getOutBizNo());
    }

    private PayoutResult mapQuery(PayoutCommand command, AlipayFundTransCommonQueryResponse response) {
        String status = upper(response.getStatus());
        State state = state(status);
        BigDecimal amount = parseMoney(response.getTransAmount());
        if (state == State.SUCCESS && (!command.requestNo().equals(response.getOutBizNo())
                || amount == null || amount.compareTo(command.amount()) != 0)) {
            state = State.UNKNOWN;
        }
        return result(command, state, response.getOrderId(), status, amount,
                response.getSubOrderErrorCode(), response.getOutBizNo());
    }

    private PayoutResult result(PayoutCommand command, State state, String orderId, String status,
                                BigDecimal amount, String failureCode, String responseOutBizNo) {
        String recipientHash = SecureUtil.sha256("ALIPAY\0" + command.recipientAccount() + "\0" + command.recipientName());
        String digest = SecureUtil.sha256(String.join("\0", value(responseOutBizNo), value(orderId),
                value(status), value(amount == null ? null : money(amount)), recipientHash));
        return new PayoutResult(state, command.requestNo(), orderId, status, amount,
                recipientHash, digest, safeCode(failureCode), null);
    }

    private PayoutResult unknown(PayoutCommand command, String failureCode) {
        return result(command, State.UNKNOWN, null, "UNKNOWN", null, failureCode, command.requestNo());
    }

    private AlipayClient client() throws Exception {
        if (!configured()) throw new IllegalStateException("ALIPAY_PAYOUT_NOT_CONFIGURED");
        com.alipay.api.AlipayConfig sdk = new com.alipay.api.AlipayConfig();
        sdk.setServerUrl(alipayConfig.getGatewayUrl());
        sdk.setAppId(alipayConfig.getAppId());
        sdk.setPrivateKey(alipayConfig.getPrivateKey());
        sdk.setFormat("json");
        sdk.setCharset("utf-8");
        sdk.setAlipayPublicKey(alipayConfig.getAlipayPublicKey());
        sdk.setSignType(alipayConfig.getSignType());
        sdk.setConnectTimeout(Math.max(1000, Math.min(alipayConfig.getConnectTimeoutMs(), 60000)));
        sdk.setReadTimeout(Math.max(1000, Math.min(alipayConfig.getReadTimeoutMs(), 60000)));
        return new DefaultAlipayClient(sdk);
    }

    private State state(String status) {
        return switch (status) {
            case "SUCCESS" -> State.SUCCESS;
            case "FAIL", "FAILED", "REFUSED" -> State.FAILED;
            case "WAIT_PAY" -> State.WAIT_USER_CONFIRM;
            case "DEALING", "PROCESSING" -> State.PROCESSING;
            default -> State.UNKNOWN;
        };
    }

    private String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    private BigDecimal parseMoney(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value).setScale(2);
        } catch (RuntimeException e) {
            return null;
        }
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
}
