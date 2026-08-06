package com.macro.mall.distribution.service;

import java.util.Map;

/**
 * 支付宝支付服务
 */
public interface AlipayService {

    /** 支付宝参数是否已完整配置；未配置时前台不得展示支付宝入口。 */
    boolean isConfigured();

    /**
     * 创建支付宝预支付订单（APP/H5支付）
     * @param orderNo 商城订单号
     * @param amount 支付金额（元）
     * @param subject 商品名称
     * @return 支付宝返回的支付参数（用于前端调起支付）
     */
    Map<String, Object> createPayOrder(String orderNo, String amount, String subject);

    /**
     * 处理支付宝异步回调
     * @param params 回调参数
     * @return "success" 表示处理成功，其他表示失败
     */
    String handleNotify(Map<String, String> params);

    /**
     * 查询支付宝订单状态
     * @param orderNo 商城订单号
     * @return 是否已支付
     */
    boolean queryOrderStatus(String orderNo);

    /**
     * 在支付宝同步跳转后，通过支付宝订单查询接口确认并幂等更新商城订单。
     * 同步跳转本身不可信，只有查询接口返回 TRADE_SUCCESS 时才允许入账。
     */
    boolean reconcileOrderFromQuery(String orderNo);

    /**
     * 发起退款
     * @param orderNo 商城订单号
     * @param refundNo 退款单号
     * @param refundAmount 退款金额（元）
     * @param reason 退款原因
     * @return 是否退款成功
     */
    boolean refund(String orderNo, String refundNo, String refundAmount, String reason);
}
