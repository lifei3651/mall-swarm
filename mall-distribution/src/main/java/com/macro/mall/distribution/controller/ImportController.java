package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.dto.ImportOrderDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.ImportService;
import com.macro.mall.distribution.service.ExternalTeamMigrationService;
import com.macro.mall.distribution.service.impl.ImportExecutionGuard;
import com.macro.mall.distribution.vo.ImportResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 导入管理控制器
 */
@Tag(name = "ImportController", description = "导入管理")
@RestController
@RequestMapping("/distribution/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final ExternalTeamMigrationService externalTeamMigrationService;
    private final ImportExecutionGuard importExecutionGuard;

    @Operation(summary = "外部团队整体平移")
    @PostMapping("/external-team/file")
    public CommonResult<ImportResultVO> migrateExternalTeam(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long anchorAgentId) {
        return CommonResult.success(importExecutionGuard.execute(
                () -> externalTeamMigrationService.migrate(file, anchorAgentId)));
    }

    @Operation(summary = "批量导入代理（Excel文件）")
    @PostMapping("/agents/file")
    public CommonResult<ImportResultVO> importAgentsByFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String batchNo) {
        DmsAdminUser operator = requireOperator();
        ImportResultVO result = importService.importAgents(file, operator.getId(), operatorName(operator), batchNo);
        return CommonResult.success(result);
    }

    @Operation(summary = "批量导入代理（数据列表）")
    @PostMapping("/agents/list")
    public CommonResult<ImportResultVO> importAgentsByList(
            @RequestBody List<ImportAgentDTO> agentList,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName) {
        DmsAdminUser operator = requireOperator();
        ImportResultVO result = importService.importAgents(agentList, operator.getId(), operatorName(operator));
        return CommonResult.success(result);
    }

    @Operation(summary = "批量导入订单（Excel文件）")
    @PostMapping("/orders/file")
    public CommonResult<ImportResultVO> importOrdersByFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String batchNo) {
        DmsAdminUser operator = requireOperator();
        ImportResultVO result = importService.importOrders(file, operator.getId(), operatorName(operator), batchNo);
        return CommonResult.success(result);
    }

    @Operation(summary = "批量导入订单（数据列表）")
    @PostMapping("/orders/list")
    public CommonResult<ImportResultVO> importOrdersByList(
            @RequestBody List<ImportOrderDTO> orderList,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName) {
        DmsAdminUser operator = requireOperator();
        ImportResultVO result = importService.importOrders(orderList, operator.getId(), operatorName(operator));
        return CommonResult.success(result);
    }

    @Operation(summary = "查询导入批次详情")
    @GetMapping("/result/{batchNo}")
    public CommonResult<ImportResultVO> getImportResult(@PathVariable String batchNo) {
        ImportResultVO result = importService.getImportResult(batchNo);
        if (result == null) {
            return CommonResult.failed("导入批次不存在");
        }
        return CommonResult.success(result);
    }

    private DmsAdminUser requireOperator() {
        DmsAdminUser operator = AdminContext.get();
        if (operator == null || operator.getId() == null) {
            Asserts.fail("未获取到后台操作人");
        }
        return operator;
    }

    private String operatorName(DmsAdminUser operator) {
        if (operator.getNickname() != null && !operator.getNickname().isBlank()) {
            return operator.getNickname();
        }
        return operator.getUsername();
    }
}
