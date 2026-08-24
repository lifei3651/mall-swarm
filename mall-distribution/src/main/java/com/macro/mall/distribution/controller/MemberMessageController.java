package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsMemberMessage;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.MessageUnreadSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MemberMessageController", description = "登录会员个人消息中心（不含商城公告）")
@RestController
@RequestMapping("/shop/messages")
@RequiredArgsConstructor
public class MemberMessageController {
    private final ShopAuthService authService;
    private final MemberMessageService messageService;

    @Operation(summary = "个人消息分页列表；列表曝光不会自动已读")
    @GetMapping
    public CommonResult<CommonPage<DmsMemberMessage>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(messageService.list(authService.requireMember(authorization), category, pageNum, pageSize));
    }

    @Operation(summary = "打开个人消息详情并标记已读")
    @GetMapping("/{id}")
    public CommonResult<DmsMemberMessage> detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(messageService.detail(authService.requireMember(authorization), id));
    }

    @Operation(summary = "总未读与五个分类未读；不统计公告")
    @GetMapping("/unread")
    public CommonResult<MessageUnreadSummaryVO> unread(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(messageService.unread(authService.requireMember(authorization)));
    }

    @Operation(summary = "明确将单条个人消息标记已读")
    @PutMapping("/{id}/read")
    public CommonResult<Boolean> read(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(messageService.markRead(authService.requireMember(authorization), id));
    }

    @Operation(summary = "将指定分类全部已读")
    @PutMapping("/read-category")
    public CommonResult<Integer> readCategory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String category) {
        return CommonResult.success(messageService.markAllRead(authService.requireMember(authorization), category));
    }

    @Operation(summary = "将全部个人消息已读")
    @PutMapping("/read-all")
    public CommonResult<Integer> readAll(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(messageService.markAllRead(authService.requireMember(authorization), null));
    }
}
