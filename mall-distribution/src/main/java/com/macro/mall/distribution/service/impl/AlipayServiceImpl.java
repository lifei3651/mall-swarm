package com.macro.mall.distribution.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.service.AlipayService;
import com.macro.mall.distribution.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {

    private static final String WAP_PRODUCT_CODE = "QUICK_WAP_WAY";
    private static final String ISV_PERMISSION_ERROR = "insufficient-isv-permissions";

    private final AlipayConfig alipayConfig;
    private final DmsShopOrderDao orderDao;
    private final ShopService shopService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isConfigured() {
        return alipayConfig.isConfigured();
    }

    private AlipayClient createClient() {
        if (!alipayConfig.isConfigured()) {
            Asserts.fail("支付宝未配置，请联系管理员");
        }
        return new DefaultAlipayClient(
                alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                "utf-8",
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );
    }

    @Override
    public Map<String, Object> createPayOrder(String orderNo, String amount, String subject) {
        try {
            AlipayClient client = createClient();

            // 使用手机网站支付（H5专用）
            AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
            request.setReturnUrl(alipayConfig.getReturnUrl());

            // 构建业务参数
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderNo);
            bizContent.put("total_amount", amount);
            bizContent.put("subject", subject);
            // 手机网站支付必须使用已在支付宝开放平台签约的 WAP 支付能力。
            bizContent.put("product_code", WAP_PRODUCT_CODE);
            bizContent.put("timeout_express", "30m");
            request.setBizContent(objectMapper.writeValueAsString(bizContent));

            AlipayTradeWapPayResponse response = client.pageExecute(request);
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.contains(ISV_PERMISSION_ERROR)) {
                log.error("支付宝手机网站支付权限不足: orderNo={}, productCode={}", orderNo, WAP_PRODUCT_CODE);
                Asserts.fail("支付宝尚未开通手机网站支付，请在支付宝开放平台为当前应用签约该能力后重试");
            }
            if (response.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("payUrl", responseBody); // 返回的是HTML表单，前端可以直接渲染或跳转
                result.put("orderNo", orderNo);
                log.info("支付宝手机网站支付表单生成: orderNo={}, productCode={}", orderNo, WAP_PRODUCT_CODE);
                return result;
            }

            log.error("支付宝预支付订单创建失败: code={}, msg={}, subMsg={}",
                    response.getCode(), response.getMsg(), response.getSubMsg());
            Asserts.fail("创建支付宝订单失败: " + (response.getSubMsg() != null ? response.getSubMsg() : response.getMsg()));
            return null;
        } catch (AlipayApiException e) {
            log.error("支付宝API调用异常", e);
            Asserts.fail("支付宝服务异常，请稍后重试");
            return null;
        } catch (Exception e) {
            log.error("创建支付宝订单异常", e);
            Asserts.fail("创建支付宝订单异常");
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(Map<String, String> params) {
        try {
            if (!isConfigured() || params == null || params.isEmpty()) {
                log.warn("支付宝回调被拒绝：支付参数未配置或通知参数为空");
                return "failure";
            }
            // 1. 验签
            boolean signVerified = verifyNotifySignature(params);

            if (!signVerified) {
                log.error("支付宝回调验签失败");
                return "failure";
            }

            // 2. 获取关键参数
            String tradeNo = params.get("trade_no");           // 支付宝交易号
            String outTradeNo = params.get("out_trade_no");    // 商户订单号
            String tradeStatus = params.get("trade_status");   // 交易状态
            String totalAmount = params.get("total_amount");   // 订单金额
            String appId = params.get("app_id");               // 应用ID

            log.info("支付宝回调: outTradeNo={}, tradeNo={}, tradeStatus={}, totalAmount={}",
                    outTradeNo, tradeNo, tradeStatus, totalAmount);

            // 3. 验证app_id
            if (!alipayConfig.getAppId().equals(appId)) {
                log.error("支付宝回调app_id不匹配: expected={}, actual={}", alipayConfig.getAppId(), appId);
                return "failure";
            }

            // 4. 查询订单
            DmsShopOrder order = orderDao.selectByOrderNoForUpdate(outTradeNo);
            if (order == null) {
                log.error("支付宝回调订单不存在: outTradeNo={}", outTradeNo);
                return "failure";
            }

            // 5. 验证金额
            BigDecimal notifyAmount = new BigDecimal(totalAmount);
            if (order.getPayAmount().compareTo(notifyAmount) != 0) {
                log.error("支付宝回调金额不匹配: orderAmount={}, notifyAmount={}", order.getPayAmount(), notifyAmount);
                return "failure";
            }

            // 6. 只接受 TRADE_SUCCESS。TRADE_FINISHED 不作为本商城的入账触发条件。
            if ("TRADE_SUCCESS".equals(tradeStatus)) {
                // 检查订单状态，避免重复处理
                if (Integer.valueOf(0).equals(order.getStatus())) {
                    // 标记订单为已支付
                    shopService.markOrderPaid(order.getId(), "ALIPAY");
                    log.info("支付宝支付成功，订单已标记为已支付: orderNo={}, tradeNo={}", outTradeNo, tradeNo);
                } else {
                    log.info("支付宝回调订单已处理过: orderNo={}, status={}", outTradeNo, order.getStatus());
                }
                return "success";
            }

            if ("TRADE_CLOSED".equals(tradeStatus)) {
                log.info("支付宝交易关闭: orderNo={}", outTradeNo);
                return "success";
            }

            log.info("支付宝回调状态未处理: orderNo={}, tradeStatus={}", outTradeNo, tradeStatus);
            return "success";
        } catch (AlipayApiException e) {
            log.error("支付宝回调处理异常", e);
            return "failure";
        } catch (Exception e) {
            log.error("支付宝回调参数处理异常", e);
            return "failure";
        }
    }

    /**
     * 独立封装回调验签，生产环境仍调用支付宝 SDK；测试环境可以只替换验签结果，
     * 从而覆盖验签失败、回调处理失败和支付宝重试语义，而无需使用真实密钥。
     */
    protected boolean verifyNotifySignature(Map<String, String> params) throws AlipayApiException {
        return AlipaySignature.rsaCheckV1(
                params,
                alipayConfig.getAlipayPublicKey(),
                "utf-8",
                alipayConfig.getSignType()
        );
    }

    @Override
    public boolean queryOrderStatus(String orderNo) {
        try {
            AlipayClient client = createClient();
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderNo);
            request.setBizContent(objectMapper.writeValueAsString(bizContent));

            AlipayTradeQueryResponse response = client.execute(request);
            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
            }

            log.warn("支付宝查询订单失败: code={}, msg={}", response.getCode(), response.getMsg());
            return false;
        } catch (Exception e) {
            log.error("支付宝查询订单异常", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reconcileOrderFromQuery(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return false;
        }
        try {
            AlipayClient client = createClient();
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderNo);
            request.setBizContent(objectMapper.writeValueAsString(bizContent));

            AlipayTradeQueryResponse response = client.execute(request);
            // 只允许 TRADE_SUCCESS 触发商城入账，避免把 FINISHED/其他状态误判成支付成功。
            if (!response.isSuccess() || !"TRADE_SUCCESS".equals(response.getTradeStatus())) {
                log.info("支付宝同步回跳查询未确认支付: orderNo={}, success={}, tradeStatus={}",
                        orderNo, response.isSuccess(), response.getTradeStatus());
                return false;
            }

            DmsShopOrder order = orderDao.selectByOrderNoForUpdate(orderNo);
            if (order == null) {
                log.warn("支付宝同步回跳查询订单不存在: orderNo={}", orderNo);
                return false;
            }
            if (Integer.valueOf(0).equals(order.getStatus())) {
                shopService.markOrderPaid(order.getId(), "ALIPAY");
                log.info("支付宝同步回跳查询确认成功，订单已标记为已支付: orderNo={}", orderNo);
            } else {
                log.info("支付宝同步回跳查询订单已处理过: orderNo={}, status={}", orderNo, order.getStatus());
            }
            return true;
        } catch (Exception e) {
            log.error("支付宝同步回跳查询异常: orderNo={}", orderNo, e);
            return false;
        }
    }

    @Override
    public boolean refund(String orderNo, String refundNo, String refundAmount, String reason) {
        try {
            AlipayClient client = createClient();
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderNo);
            bizContent.put("out_request_no", refundNo);
            bizContent.put("refund_amount", refundAmount);
            bizContent.put("refund_reason", reason);
            request.setBizContent(objectMapper.writeValueAsString(bizContent));

            AlipayTradeRefundResponse response = client.execute(request);
            if (response.isSuccess()) {
                log.info("支付宝退款成功: orderNo={}, refundNo={}, amount={}", orderNo, refundNo, refundAmount);
                return true;
            }

            log.error("支付宝退款失败: code={}, msg={}, subMsg={}",
                    response.getCode(), response.getMsg(), response.getSubMsg());
            return false;
        } catch (Exception e) {
            log.error("支付宝退款异常", e);
            return false;
        }
    }
}
