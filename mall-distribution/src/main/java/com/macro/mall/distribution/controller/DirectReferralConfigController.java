package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.DirectReferralConfigDTO;
import com.macro.mall.distribution.service.DirectReferralConfigService;
import com.macro.mall.distribution.vo.DirectReferralConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/distribution/bonus-config/direct-referral")
@RequiredArgsConstructor
public class DirectReferralConfigController {
    private final DirectReferralConfigService service;

    @GetMapping
    public CommonResult<DirectReferralConfigVO> current() {
        return CommonResult.success(service.current());
    }

    @PutMapping
    public CommonResult<DirectReferralConfigVO> save(@RequestBody DirectReferralConfigDTO dto) {
        return CommonResult.success(service.save(dto));
    }
}
