package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.entity.DmsMessageTemplate;
import com.macro.mall.distribution.service.MemberMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shop/admin/message-operations")
@RequiredArgsConstructor
public class AdminMessageOperationController {
    private final MemberMessageService messageService;

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
    // 刻意不提供重发接口：特别是资金消息，普通管理员不能重放业务事实。
}
