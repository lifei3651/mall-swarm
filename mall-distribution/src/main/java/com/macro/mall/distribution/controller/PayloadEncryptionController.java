package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import com.macro.mall.distribution.vo.PayloadEncryptionKeyVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/security/payload-encryption")
@RequiredArgsConstructor
public class PayloadEncryptionController {

    private final PayloadEncryptionService payloadEncryptionService;

    @GetMapping("/key")
    public CommonResult<PayloadEncryptionKeyVO> key(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        return CommonResult.success(payloadEncryptionService.issueChallenge());
    }
}
