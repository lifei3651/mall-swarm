package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.RealNameVerifyDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.RealNameVerificationService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.RealNameStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ShopRealNameController", description = "商城会员实名认证")
@RestController
@RequestMapping("/shop/real-name")
@RequiredArgsConstructor
public class ShopRealNameController {
    private final ShopAuthService authService;
    private final RealNameVerificationService realNameService;

    @Operation(summary = "查询当前账号实名认证状态")
    @GetMapping("/status")
    public CommonResult<RealNameStatusVO> status(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(realNameService.getStatus(authService.requireMember(authorization)));
    }

    @Operation(summary = "提交姓名和身份证号进行权威核验")
    @PostMapping("/verify")
    public CommonResult<RealNameStatusVO> verify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RealNameVerifyDTO dto) {
        DmsShopMember member = authService.requireMember(authorization);
        return CommonResult.success(realNameService.verify(member, dto));
    }
}
