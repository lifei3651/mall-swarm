package com.macro.mall.distribution.controller;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsOperationLog;
import com.macro.mall.distribution.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OperationLogController", description = "后台操作日志")
@RestController
@RequestMapping("/distribution/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "查询操作日志")
    @GetMapping
    public CommonResult<CommonPage<DmsOperationLog>> listLogs(@RequestParam(required = false) String moduleName,
                                                              @RequestParam(required = false) String targetType,
                                                              @RequestParam(required = false) String targetId,
                                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(operationLogService.listLogs(moduleName, targetType, targetId)));
    }
}
