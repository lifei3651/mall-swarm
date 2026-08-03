package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.ErpShipmentCallbackDTO;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsErpSyncTask;
import com.macro.mall.distribution.service.ErpIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "ErpIntegrationController", description = "ERP集成：聚水潭、旺店通、金蝶")
@RestController
@RequestMapping("/distribution/erp")
@RequiredArgsConstructor
public class ErpIntegrationController {
    private final ErpIntegrationService erpIntegrationService;

    @Operation(summary = "ERP配置列表")
    @GetMapping("/integrations")
    public CommonResult<List<DmsErpIntegration>> list(@RequestParam(required = false) Long tenantId) { return CommonResult.success(erpIntegrationService.listIntegrations(tenantId)); }

    @Operation(summary = "保存ERP配置")
    @PostMapping("/integrations")
    public CommonResult<DmsErpIntegration> save(@RequestBody DmsErpIntegration integration) { return CommonResult.success(erpIntegrationService.saveIntegration(integration)); }

    @Operation(summary = "ERP推单任务")
    @GetMapping("/tasks")
    public CommonResult<List<DmsErpSyncTask>> tasks(@RequestParam(required = false) Long integrationId, @RequestParam(required = false) Integer status) { return CommonResult.success(erpIntegrationService.listTasks(integrationId, status)); }

    @Operation(summary = "手动重试ERP推单")
    @PostMapping("/tasks/{id}/retry")
    public CommonResult<Boolean> retry(@PathVariable Long id) { return CommonResult.success(erpIntegrationService.retryTask(id)); }

    @Operation(summary = "ERP发货回传")
    @PostMapping("/callbacks/shipment")
    public CommonResult<Boolean> shipment(@RequestBody ErpShipmentCallbackDTO callback) { return CommonResult.success(erpIntegrationService.receiveShipment(callback)); }
}
