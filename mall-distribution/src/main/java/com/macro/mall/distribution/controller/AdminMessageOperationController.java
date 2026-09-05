package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.entity.DmsMessageTemplate;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.notification.NotificationOperationsViewService;
import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import com.macro.mall.distribution.vo.NotificationRuntimeStatusVO;
import com.macro.mall.distribution.vo.WeChatShippingOperationsVO;
import com.macro.mall.distribution.dto.WeChatShippingRetryDTO;
import com.macro.mall.distribution.service.WeChatShippingOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shop/admin/message-operations")
@RequiredArgsConstructor
public class AdminMessageOperationController {
    private final MemberMessageService messageService;
    private final NotificationOperationsViewService notificationViewService;
    private final WeChatShippingOperationsService shippingOperationsService;

    @GetMapping("/templates")
    public CommonResult<List<DmsMessageTemplate>> templates() { return CommonResult.success(messageService.listTemplates()); }

    @PutMapping("/templates/{id}")
    public CommonResult<DmsMessageTemplate> updateTemplate(@PathVariable Long id,
                                                            @Valid @RequestBody DmsMessageTemplate input) {
        return CommonResult.success(messageService.updateTemplate(id, input));
    }

    @GetMapping("/channels")
    public CommonResult<List<DmsMessageChannelConfig>> channels() { return CommonResult.success(messageService.listChannels()); }

    @PutMapping("/channels/{id}/in-app")
    public CommonResult<DmsMessageChannelConfig> updateInApp(@PathVariable Long id,
                                                              @RequestParam boolean enabled) {
        return CommonResult.success(messageService.updateInAppChannel(id, enabled));
    }

    @GetMapping("/deliveries")
    public CommonResult<CommonPage<DmsMessageDeliveryTask>> deliveries(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(messageService.listDeliveries(channel, status, pageNum, pageSize));
    }
    @GetMapping("/runtime")
    public CommonResult<NotificationRuntimeStatusVO> runtime() { return CommonResult.success(notificationViewService.runtime()); }
    @GetMapping("/budgets")
    public CommonResult<List<DmsMessageCostBudget>> budgets() { return CommonResult.success(notificationViewService.budgets()); }
    @GetMapping("/deliveries/{taskId}/attempts")
    public CommonResult<List<DmsMessageDeliveryAttempt>> attempts(@PathVariable Long taskId) {
        return CommonResult.success(notificationViewService.attempts(taskId));
    }
    @GetMapping("/shipping-synchronizations")
    public CommonResult<WeChatShippingOperationsVO> shippingSynchronizations(
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(shippingOperationsService.list(status, pageNum, pageSize));
    }
    @PostMapping("/shipping-synchronizations/{taskId}/retry")
    public CommonResult<Void> retryShipping(@PathVariable Long taskId, @Valid @RequestBody WeChatShippingRetryDTO input) {
        shippingOperationsService.retry(taskId, input.revision());
        return CommonResult.success(null);
    }
    // 不提供资金消息重发；上述操作仅重新同步既有物流事实，不改变交易或资金。
}
