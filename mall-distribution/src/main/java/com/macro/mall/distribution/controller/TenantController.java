package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.TenantService;
import com.macro.mall.distribution.service.CustomerDeliveryReadinessService;
import com.macro.mall.distribution.vo.CustomerDeliveryReadinessVO;
import com.macro.mall.distribution.vo.TenantLegalTemplatesVO;
import com.macro.mall.distribution.vo.TenantConfigVersionVO;
import com.github.pagehelper.PageHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "TenantController", description = "客户公司与主题配置")
@RestController
@RequestMapping("/distribution/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final CustomerDeliveryReadinessService deliveryReadinessService;

    @Operation(summary = "查询客户公司列表")
    @GetMapping("/list")
    public CommonResult<CommonPage<DmsTenant>> listTenants(@RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(tenantService.listTenants()));
    }

    @Operation(summary = "保存客户公司配置")
    @PostMapping
    public CommonResult<DmsTenant> saveTenant(@Valid @RequestBody DmsTenant tenant) {
        return CommonResult.success(tenantService.saveTenant(tenant));
    }

    @Operation(summary = "获取商城协议默认模板")
    @GetMapping("/legal-templates")
    public CommonResult<TenantLegalTemplatesVO> legalTemplates() {
        return CommonResult.success(tenantService.getLegalTemplates());
    }

    @Operation(summary = "客户交付就绪预检")
    @GetMapping("/{tenantId}/delivery-readiness")
    public CommonResult<CustomerDeliveryReadinessVO> deliveryReadiness(@PathVariable Long tenantId) {
        return CommonResult.success(deliveryReadinessService.evaluate(tenantId));
    }

    @Operation(summary = "更新客户公司状态")
    @PutMapping("/{id}/status")
    public CommonResult<Boolean> updateTenantStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(tenantService.updateTenantStatus(id, status));
    }

    @Operation(summary = "查询奖金规则版本")
    @GetMapping("/{tenantId}/rule-versions")
    public CommonResult<List<DmsCommissionRuleVersion>> listRuleVersions(@PathVariable Long tenantId) {
        return CommonResult.success(tenantService.listRuleVersions(tenantId));
    }

    @Operation(summary = "查询前端展示开关")
    @GetMapping("/{tenantId}/display-config")
    public CommonResult<DmsTenantDisplayConfig> getDisplayConfig(@PathVariable Long tenantId) {
        return CommonResult.success(tenantService.getDisplayConfig(tenantId));
    }

    @Operation(summary = "保存前端展示开关")
    @PostMapping("/display-config")
    public CommonResult<DmsTenantDisplayConfig> saveDisplayConfig(@Valid @RequestBody DmsTenantDisplayConfig config) {
        return CommonResult.success(tenantService.saveDisplayConfig(config));
    }

    @Operation(summary = "查询商城配置历史版本")
    @GetMapping("/{tenantId}/config-versions")
    public CommonResult<List<TenantConfigVersionVO>> listConfigVersions(@PathVariable Long tenantId) {
        return CommonResult.success(tenantService.listConfigVersions(tenantId));
    }

    @Operation(summary = "恢复商城配置历史版本")
    @PostMapping("/{tenantId}/config-versions/{versionId}/restore")
    public CommonResult<DmsTenant> restoreConfigVersion(@PathVariable Long tenantId,
                                                        @PathVariable Long versionId) {
        return CommonResult.success(tenantService.restoreConfigVersion(tenantId, versionId));
    }
}
