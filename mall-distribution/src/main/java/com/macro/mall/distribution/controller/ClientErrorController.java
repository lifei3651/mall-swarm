package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.log.SensitiveLogSanitizer;
import com.macro.mall.distribution.dto.ClientErrorReportDTO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/shop")
public class ClientErrorController {
    @PostMapping("/client-errors")
    public CommonResult<Boolean> report(@Valid @RequestBody ClientErrorReportDTO report) {
        log.warn("client_runtime_error app={} source={} route={} name={} message={} info={}",
                report.getApp(), report.getSource(), safe(report.getRoute()), safe(report.getName()),
                safe(report.getMessage()), safe(report.getInfo()));
        return CommonResult.success(true);
    }

    private String safe(String value) {
        return SensitiveLogSanitizer.sanitizeText(value == null ? "" : value);
    }
}
