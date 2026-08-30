package com.macro.mall.distribution.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.log.SensitiveLogSanitizer;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.WeChatPayService;
import com.macro.mall.distribution.vo.WeChatPayParametersVO;
import com.macro.mall.distribution.wechat.WeChatPayGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatPayServiceImpl implements WeChatPayService {

    private final WeChatPayProperties payProperties;
    private final WeChatMiniProgramProperties miniProgramProperties;
    private final WeChatPayGateway gateway;
    private final DmsShopOrderDao orderDao;
    private final DmsShopTradeDao tradeDao;
    private final DmsShopMemberDao memberDao;
    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final ShopService shopService;

    @Override
    public boolean isConfigured() {
        return payProperties.isConfigured() && miniProgramProperties.loginReady();
    }

    @Override
    public WeChatPayParametersVO createPayOrder(Long checkoutOrOrderId, DmsShopMember member) {
        requireConfigured();
        if (member == null) Asserts.fail("请先登录");
        PaymentTarget target = paymentTarget(checkoutOrOrderId, false);
        assertOwnerAndPayable(target, member);
        String openId = requireOpenId(member);
        WeChatPayGateway.PrepayResult result;
        try {
            result = gateway.prepay(new WeChatPayGateway.PrepayCommand(target.paymentNo(),
                    fenInt(target.payAmount()), paymentDescription(target), openId));
        } catch (Exception ex) {
            log.error("微信支付预下单失败: paymentNo={}, error={}", target.paymentNo(),
                    SensitiveLogSanitizer.sanitizeText(ex.getMessage()));
            Asserts.fail("微信支付下单失败，请稍后重试");
            return null;
        }
        if (result == null || blank(result.timeStamp()) || blank(result.nonceStr())
                || blank(result.packageValue()) || blank(result.signType()) || blank(result.paySign())) {
            Asserts.fail("微信支付返回参数不完整，请稍后重试");
        }
        if (!miniProgramProperties.getAppId().trim().equals(result.appId())) {
            Asserts.fail("微信支付应用与当前小程序不一致，已停止支付");
        }
        return WeChatPayParametersVO.builder()
                .orderId(checkoutOrOrderId)
                .paymentNo(target.paymentNo())
                .timeStamp(result.timeStamp())
                .nonceStr(result.nonceStr())
                .packageValue(result.packageValue())
                .signType(result.signType())
                .paySign(result.paySign())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reconcileOrder(Long checkoutOrOrderId, DmsShopMember member) {
        requireConfigured();
        if (member == null) Asserts.fail("请先登录");
        PaymentTarget target = paymentTarget(checkoutOrOrderId, false);
        if (target == null || !member.getUserId().equals(target.userId())) Asserts.fail("无权查询此订单");
        if (!"WECHAT".equalsIgnoreCase(target.payType())) Asserts.fail("该订单不是微信支付");
        if (isPaid(target.status())) return true;
        if (!Integer.valueOf(0).equals(target.status()) && !isUnpaidClosed(target)) return false;
        try {
            WeChatPayGateway.PaymentResult result = gateway.query(target.paymentNo());
            if (!"SUCCESS".equals(result == null ? null : result.state())) return false;
            validatePaymentResult(result, target);
            return applyConfirmedPayment(target);
        } catch (Exception ex) {
            log.error("微信支付查单失败: paymentNo={}, error={}", target.paymentNo(),
                    SensitiveLogSanitizer.sanitizeText(ex.getMessage()));
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(String paymentNo) {
        if (!isConfigured() || blank(paymentNo)) return;
        try {
            gateway.close(paymentNo);
            log.info("微信支付订单已随本地待支付订单关闭: paymentNo={}", paymentNo);
            return;
        } catch (Exception closeError) {
            log.warn("微信支付关单未直接完成，开始查单确认: paymentNo={}, error={}", paymentNo,
                    SensitiveLogSanitizer.sanitizeText(closeError.getMessage()));
        }
        try {
            WeChatPayGateway.PaymentResult result = gateway.query(paymentNo);
            if (result != null && "SUCCESS".equals(result.state())) {
                PaymentTarget target = paymentTargetByPaymentNo(paymentNo, true);
                validatePaymentResult(result, target);
                applyConfirmedPayment(target);
                return;
            }
            log.error("微信支付关单失败且当前未查到成功支付，等待后续回调兜底: paymentNo={}, state={}",
                    paymentNo, result == null ? null : result.state());
        } catch (Exception queryError) {
            log.error("微信支付关单失败后的查单也未完成，等待后续回调兜底: paymentNo={}, error={}", paymentNo,
                    SensitiveLogSanitizer.sanitizeText(queryError.getMessage()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentNotification(WeChatPayGateway.NotificationRequest request) {
        requireConfigured();
        WeChatPayGateway.PaymentResult result;
        try {
            result = gateway.parsePaymentNotification(request);
        } catch (Exception ex) {
            log.warn("微信支付回调验签或解密失败: {}", SensitiveLogSanitizer.sanitizeText(ex.getMessage()));
            throw ex;
        }
        if (result == null || !"SUCCESS".equals(result.state())) Asserts.fail("微信支付通知状态不正确");
        PaymentTarget target = paymentTargetByPaymentNo(result.paymentNo(), true);
        validatePaymentResult(result, target);
        if (!applyConfirmedPayment(target)) Asserts.fail("微信支付结果尚未完成处理");
    }

    @Override
    public RefundState requestRefund(String paymentNo, String refundNo, BigDecimal refundAmount,
                                     BigDecimal paymentAmount, String reason) {
        requireConfigured();
        try {
            WeChatPayGateway.RefundResult result = gateway.refund(new WeChatPayGateway.RefundCommand(
                    paymentNo, refundNo, fenLong(refundAmount), fenLong(paymentAmount), safeReason(reason)));
            validateRefundResult(result, paymentNo, refundNo, refundAmount, paymentAmount);
            if ("SUCCESS".equals(result.state())) return RefundState.COMPLETED;
            if ("PROCESSING".equals(result.state())) return RefundState.PROCESSING;
            log.error("微信退款被渠道拒绝: paymentNo={}, refundNo={}, state={}", paymentNo, refundNo, result.state());
            return RefundState.FAILED;
        } catch (Exception ex) {
            log.error("微信退款申请失败: paymentNo={}, refundNo={}, error={}", paymentNo, refundNo,
                    SensitiveLogSanitizer.sanitizeText(ex.getMessage()));
            return RefundState.FAILED;
        }
    }

    @Override
    public WeChatPayGateway.RefundNotification parseRefundNotification(WeChatPayGateway.NotificationRequest request) {
        requireConfigured();
        try {
            return gateway.parseRefundNotification(request);
        } catch (Exception ex) {
            log.warn("微信退款回调验签或解密失败: {}", SensitiveLogSanitizer.sanitizeText(ex.getMessage()));
            throw ex;
        }
    }

    private boolean applyConfirmedPayment(PaymentTarget initialTarget) {
        PaymentTarget target = paymentTargetByPaymentNo(initialTarget.paymentNo(), true);
        if (target == null) Asserts.fail("微信支付订单不存在");
        if (isPaid(target.status())) return true;
        if (isLatePaymentRefunded(target)) return true;
        if (isUnpaidClosed(target)) return refundLatePayment(target);
        if (!Integer.valueOf(0).equals(target.status())) return false;
        if (target.grouped()) shopService.markCheckoutPaid(target.id(), "WECHAT");
        else shopService.markOrderPaid(target.id(), "WECHAT");
        log.info("微信支付确认成功，交易已标记为已支付: paymentNo={}", target.paymentNo());
        return true;
    }

    private boolean refundLatePayment(PaymentTarget target) {
        String refundNo = "LATEPAY-" + SecureUtil.sha256(target.paymentNo()).substring(0, 32);
        RefundState state = requestRefund(target.paymentNo(), refundNo, target.payAmount(), target.payAmount(),
                "订单超时关闭后的支付自动退回");
        if (state == RefundState.FAILED) return false;
        if (state == RefundState.COMPLETED) markLateRefunded(target);
        log.warn("微信支付在本地关单后到账，已发起自动退款: paymentNo={}, refundState={}",
                target.paymentNo(), state);
        return true;
    }

    private void markLateRefunded(PaymentTarget target) {
        int marked = target.grouped() ? tradeDao.markLateRefunded(target.id()) : orderDao.markLateRefunded(target.id());
        if (marked != 1 && !isLatePaymentRefunded(paymentTargetByPaymentNo(target.paymentNo(), false))) {
            throw new IllegalStateException("微信迟到支付已退款，但本地幂等标记保存失败");
        }
    }

    private void validatePaymentResult(WeChatPayGateway.PaymentResult result, PaymentTarget target) {
        if (target == null) Asserts.fail("微信支付订单不存在");
        if (!target.paymentNo().equals(result.paymentNo())) Asserts.fail("微信支付单号不匹配");
        if (!miniProgramProperties.getAppId().trim().equals(result.appId())) Asserts.fail("微信支付AppID不匹配");
        if (!payProperties.getMchId().trim().equals(result.mchId())) Asserts.fail("微信支付商户号不匹配");
        if (!"CNY".equalsIgnoreCase(result.currency()) || result.totalFen() == null
                || result.totalFen() != fenInt(target.payAmount())) Asserts.fail("微信支付金额或币种不匹配");
        String expectedOpenId = openIdForUser(target.userId());
        if (blank(result.openId()) || !constantEquals(expectedOpenId, result.openId())) {
            Asserts.fail("微信支付用户身份不匹配");
        }
    }

    private void validateRefundResult(WeChatPayGateway.RefundResult result, String paymentNo, String refundNo,
                                      BigDecimal refundAmount, BigDecimal paymentAmount) {
        if (result == null || !paymentNo.equals(result.paymentNo()) || !refundNo.equals(result.refundNo())) {
            Asserts.fail("微信退款单号不匹配");
        }
        if (!"CNY".equalsIgnoreCase(result.currency()) || result.refundFen() == null || result.totalFen() == null
                || result.refundFen() != fenLong(refundAmount) || result.totalFen() != fenLong(paymentAmount)) {
            Asserts.fail("微信退款金额或币种不匹配");
        }
    }

    private void assertOwnerAndPayable(PaymentTarget target, DmsShopMember member) {
        if (target == null) Asserts.fail("订单不存在");
        if (!member.getUserId().equals(target.userId())) Asserts.fail("无权支付此订单");
        if (!Integer.valueOf(0).equals(target.status())) Asserts.fail("订单状态不允许支付");
        if (!"WECHAT".equalsIgnoreCase(target.payType())) Asserts.fail("该订单不是微信支付");
    }

    private String requireOpenId(DmsShopMember member) {
        if (member.getId() == null) Asserts.fail("商城账号信息不完整，请重新登录");
        DmsWechatMiniProgramIdentity identity = identityDao.selectByMember(TenantContext.getTenantId(), appIdHash(), member.getId());
        if (identity == null || blank(identity.getOpenId())) Asserts.fail("当前商城账号未绑定本小程序，请重新使用微信登录");
        if (!member.getUserId().equals(identity.getUserId())) Asserts.fail("小程序身份与商城账号不一致");
        return identity.getOpenId();
    }

    private String openIdForUser(Long userId) {
        DmsShopMember member = memberDao.selectByUserId(userId);
        return member == null ? null : requireOpenId(member);
    }

    private String appIdHash() {
        return SecureUtil.sha256(miniProgramProperties.getAppId().trim());
    }

    private PaymentTarget paymentTarget(Long checkoutOrOrderId, boolean lock) {
        if (checkoutOrOrderId == null) return null;
        DmsShopOrder order = lock ? orderDao.selectByIdForUpdate(checkoutOrOrderId) : orderDao.selectById(checkoutOrOrderId);
        if (order != null && order.getTradeId() == null) return orderTarget(order);
        Long tradeId = order == null ? checkoutOrOrderId : order.getTradeId();
        DmsShopTrade trade = lock ? tradeDao.selectByIdForUpdate(tradeId) : tradeDao.selectById(tradeId);
        return trade == null ? null : tradeTarget(trade);
    }

    private PaymentTarget paymentTargetByPaymentNo(String paymentNo, boolean lock) {
        if (blank(paymentNo)) return null;
        DmsShopTrade trade = lock ? tradeDao.selectByTradeNoForUpdate(paymentNo) : tradeDao.selectByTradeNo(paymentNo);
        if (trade != null) return tradeTarget(trade);
        DmsShopOrder order = lock ? orderDao.selectByOrderNoForUpdate(paymentNo) : orderDao.selectByOrderNo(paymentNo);
        return order == null ? null : orderTarget(order);
    }

    private PaymentTarget orderTarget(DmsShopOrder order) {
        return new PaymentTarget(order.getId(), order.getUserId(), order.getStatus(), order.getPayType(),
                order.getPayAmount(), blank(order.getPaymentOrderNo()) ? order.getOrderNo() : order.getPaymentOrderNo(),
                false, order.getPayTime() == null, order.getLateRefundFlag());
    }

    private PaymentTarget tradeTarget(DmsShopTrade trade) {
        return new PaymentTarget(trade.getId(), trade.getUserId(), trade.getStatus(), trade.getPayType(),
                trade.getPayAmount(), trade.getTradeNo(), true, trade.getPayTime() == null, trade.getLateRefundFlag());
    }

    private boolean isPaid(Integer status) {
        return Integer.valueOf(1).equals(status) || Integer.valueOf(2).equals(status) || Integer.valueOf(3).equals(status);
    }

    private boolean isUnpaidClosed(PaymentTarget target) {
        return target != null && Integer.valueOf(4).equals(target.status()) && target.payTimeMissing()
                && !Integer.valueOf(1).equals(target.lateRefundFlag());
    }

    private boolean isLatePaymentRefunded(PaymentTarget target) {
        return target != null && Integer.valueOf(1).equals(target.lateRefundFlag());
    }

    private int fenInt(BigDecimal amount) {
        long value = fenLong(amount);
        if (value <= 0 || value > Integer.MAX_VALUE) Asserts.fail("微信支付金额超出支持范围");
        return Math.toIntExact(value);
    }

    private long fenLong(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) Asserts.fail("微信支付金额不正确");
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    private String paymentDescription(PaymentTarget target) {
        String value = (target.grouped() ? "商城合并订单-" : "商城订单-") + target.paymentNo();
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private String safeReason(String reason) {
        String value = reason == null || reason.isBlank() ? "商城退款" : reason.trim();
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfigured() {
        if (!isConfigured()) Asserts.fail("微信支付未配置，请联系管理员");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record PaymentTarget(Long id, Long userId, Integer status, String payType, BigDecimal payAmount,
                                 String paymentNo, boolean grouped, boolean payTimeMissing, Integer lateRefundFlag) {
    }
}
