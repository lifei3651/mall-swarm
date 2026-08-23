package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.TencentLiveCallbackDTO;
import com.macro.mall.distribution.service.LiveCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop/live/callbacks")
@RequiredArgsConstructor
public class LiveCallbackController {

    private final LiveCallbackService liveCallbackService;

    @PostMapping("/tencent")
    public CommonResult<Boolean> tencent(@Valid @RequestBody TencentLiveCallbackDTO dto) {
        return CommonResult.success(liveCallbackService.handleTencent(dto));
    }
}
