package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.service.AlipayService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.entity.DmsShopMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 商城支付控制器
 */
@Slf4j
@Tag(name = "ShopPayController", description = "商城支付")
@RestController
@RequestMapping({"/shop/pay", "/pay"})
@RequiredArgsConstructor
public class ShopPayController {

    private final AlipayService alipayService;
    private final ShopAuthService authService;
    private final DmsShopOrderDao orderDao;

    @Operation(summary = "创建支付宝支付订单")
    @PostMapping("/alipay/create")
    public CommonResult<Map<String, Object>> createAlipayOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long orderId) {
        DmsShopMember member = authService.requireMember(authorization);

        // 查询订单
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) {
            return CommonResult.failed("订单不存在");
        }
        if (!member.getUserId().equals(order.getUserId())) {
            return CommonResult.failed("无权支付此订单");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            return CommonResult.failed("订单状态不允许支付");
        }
        if (!"ALIPAY".equalsIgnoreCase(order.getPayType())) {
            return CommonResult.failed("该订单不是支付宝支付");
        }

        // 创建支付宝订单
        String subject = "商城订单-" + order.getOrderNo();
        if (subject.length() > 256) {
            subject = subject.substring(0, 256);
        }

        Map<String, Object> result = alipayService.createPayOrder(
                order.getOrderNo(),
                order.getPayAmount().toPlainString(),
                subject
        );
        return CommonResult.success(result);
    }

    @Operation(summary = "支付宝异步回调")
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }

        String result = alipayService.handleNotify(params);
        return result;
    }

    /**
     * 支付宝同步跳转只用于回到商城页面，最终支付结果必须以异步通知为准。
     */
    @Operation(summary = "支付宝同步跳转")
    @GetMapping("/alipay/return")
    public ResponseEntity<Void> alipayReturn() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, "/orders");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @Operation(summary = "查询支付宝订单状态")
    @GetMapping("/alipay/query")
    public CommonResult<Boolean> queryAlipayOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long orderId) {
        DmsShopMember member = authService.requireMember(authorization);

        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) {
            return CommonResult.failed("订单不存在");
        }
        if (!member.getUserId().equals(order.getUserId())) {
            return CommonResult.failed("无权查询此订单");
        }

        boolean paid = alipayService.queryOrderStatus(order.getOrderNo());
        return CommonResult.success(paid);
    }

    @Operation(summary = "获取支付配置状态")
    @GetMapping("/config")
    public CommonResult<Map<String, Object>> getPayConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("alipayEnabled", alipayService.isConfigured());
        return CommonResult.success(config);
    }
}
