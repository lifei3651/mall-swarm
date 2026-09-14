package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.WithdrawalSettingsUpdateDTO;
import com.macro.mall.distribution.service.WithdrawalSettingsService;
import com.macro.mall.distribution.vo.WithdrawalSettingsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/distribution/withdraw/settings")
@RequiredArgsConstructor
public class WithdrawalSettingsController {
    private final WithdrawalSettingsService service;

    @GetMapping
    public CommonResult<WithdrawalSettingsVO> current() { return CommonResult.success(service.current()); }

    @PutMapping
    public CommonResult<WithdrawalSettingsVO> update(@Valid @RequestBody WithdrawalSettingsUpdateDTO dto) {
        return CommonResult.success(service.update(dto), "提现规则已保存");
    }
}
