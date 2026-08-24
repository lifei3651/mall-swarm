package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.notification.NotificationReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/shop/notification/receipts")
@RequiredArgsConstructor
public class NotificationReceiptController {
    private final NotificationReceiptService receiptService;

    @PostMapping("/{tenantId}/{channel}/{provider}")
    public CommonResult<Boolean> receipt(@PathVariable Long tenantId,@PathVariable String channel,@PathVariable String provider,
                                         @RequestHeader(value="X-Notification-Timestamp",required=false) String timestamp,
                                         @RequestHeader(value="X-Notification-Signature",required=false) String signature,
                                         @RequestBody byte[] body) {
        return CommonResult.success(receiptService.accept(tenantId,channel,provider,
                Map.of("x-notification-timestamp",timestamp==null?"":timestamp,
                       "x-notification-signature",signature==null?"":signature),body));
    }
}
