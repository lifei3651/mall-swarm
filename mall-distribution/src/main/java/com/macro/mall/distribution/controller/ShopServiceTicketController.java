package com.macro.mall.distribution.controller;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dto.ShopServiceTicketAdminActionDTO;
import com.macro.mall.distribution.dto.ShopServiceTicketCreateDTO;
import com.macro.mall.distribution.dto.ShopServiceTicketReplyDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopServiceTicket;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopServiceTicketService;
import com.macro.mall.distribution.vo.ShopServiceTicketDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ShopServiceTicketController", description = "会员客服咨询、投诉与处理进度")
@RestController
@RequiredArgsConstructor
public class ShopServiceTicketController {
    private final ShopAuthService authService;
    private final ShopServiceTicketService ticketService;

    @Operation(summary = "会员自己的客服工单列表")
    @GetMapping("/shop/service-tickets")
    public CommonResult<CommonPage<DmsShopServiceTicket>> memberList(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(ticketService.listMember(member(authorization), status, pageNum, pageSize));
    }

    @Operation(summary = "会员提交咨询或投诉")
    @PostMapping("/shop/service-tickets")
    @Idempotent(timeout = 30, message = "工单正在提交，请勿重复操作")
    public CommonResult<ShopServiceTicketDetailVO> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopServiceTicketCreateDTO input) {
        return CommonResult.success(ticketService.create(member(authorization), input));
    }

    @Operation(summary = "会员查看自己的客服工单")
    @GetMapping("/shop/service-tickets/{id}")
    public CommonResult<ShopServiceTicketDetailVO> memberDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(ticketService.memberDetail(member(authorization), id));
    }

    @Operation(summary = "会员补充客服工单信息；已答复工单会重新进入待处理")
    @PostMapping("/shop/service-tickets/{id}/replies")
    @Idempotent(timeout = 30, message = "回复正在提交，请勿重复操作")
    public CommonResult<ShopServiceTicketDetailVO> memberReply(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id, @Valid @RequestBody ShopServiceTicketReplyDTO input) {
        return CommonResult.success(ticketService.memberReply(member(authorization), id, input.getContent()));
    }

    @Operation(summary = "会员确认问题已解决并关闭工单")
    @PutMapping("/shop/service-tickets/{id}/close")
    public CommonResult<ShopServiceTicketDetailVO> memberClose(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return CommonResult.success(ticketService.memberClose(member(authorization), id));
    }

    @Operation(summary = "后台客服工单队列")
    @GetMapping("/shop/admin/service-tickets")
    public CommonResult<CommonPage<DmsShopServiceTicket>> adminList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(ticketService.listAdmin(admin(), keyword, status, type, pageNum, pageSize));
    }

    @Operation(summary = "后台查看客服工单及完整对话")
    @GetMapping("/shop/admin/service-tickets/{id}")
    public CommonResult<ShopServiceTicketDetailVO> adminDetail(@PathVariable Long id) {
        return CommonResult.success(ticketService.adminDetail(admin(), id));
    }

    @Operation(summary = "后台回复并更新工单状态")
    @PostMapping("/shop/admin/service-tickets/{id}/replies")
    @Idempotent(timeout = 30, message = "客服回复正在提交，请勿重复操作")
    public CommonResult<ShopServiceTicketDetailVO> adminReply(
            @PathVariable Long id, @Valid @RequestBody ShopServiceTicketAdminActionDTO input) {
        return CommonResult.success(ticketService.adminReply(admin(), id, input));
    }

    private DmsShopMember member(String authorization) {
        return authService.requireMember(authorization);
    }

    private DmsAdminUser admin() {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null) Asserts.unauthorized("请先登录管理后台");
        return admin;
    }
}
