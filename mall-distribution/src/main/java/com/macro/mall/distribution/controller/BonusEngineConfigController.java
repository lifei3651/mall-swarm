package com.macro.mall.distribution.controller;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.BonusSimulationDTO;
import com.macro.mall.distribution.entity.DmsBonusCalculationSnapshot;
import com.macro.mall.distribution.entity.DmsOrderPvDetail;
import com.macro.mall.distribution.entity.DmsProductPvConfig;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.BonusEngineConfigService;
import com.macro.mall.distribution.vo.BonusSimulationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "BonusEngineConfigController", description = "奖金引擎配置")
@RestController
@RequestMapping("/distribution/bonus-config")
@RequiredArgsConstructor
public class BonusEngineConfigController {

    private final BonusEngineConfigService bonusEngineConfigService;

    @Operation(summary = "获取前端展示开关")
    @GetMapping("/display/{tenantId}")
    public CommonResult<DmsTenantDisplayConfig> getDisplayConfig(@PathVariable Long tenantId) {
        return CommonResult.success(bonusEngineConfigService.getDisplayConfig(tenantId));
    }

    @Operation(summary = "保存前端展示开关")
    @PutMapping("/display/{tenantId}")
    public CommonResult<DmsTenantDisplayConfig> saveDisplayConfig(@PathVariable Long tenantId,
                                                                  @Valid @RequestBody DmsTenantDisplayConfig config) {
        config.setTenantId(tenantId);
        return CommonResult.success(bonusEngineConfigService.saveDisplayConfig(config));
    }

    @Operation(summary = "查询商品 PV 配置")
    @GetMapping("/pv/products")
    public CommonResult<CommonPage<DmsProductPvConfig>> listProductPvConfigs(@RequestParam Long tenantId,
                                                                             @RequestParam(required = false) String keyword,
                                                                             @RequestParam(required = false) Integer status,
                                                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(bonusEngineConfigService.listProductPvConfigs(tenantId, keyword, status)));
    }

    @Operation(summary = "保存商品 PV 配置")
    @PostMapping("/pv/products")
    public CommonResult<DmsProductPvConfig> saveProductPvConfig(@Valid @RequestBody DmsProductPvConfig config) {
        return CommonResult.success(bonusEngineConfigService.saveProductPvConfig(config));
    }

    @Operation(summary = "更新商品 PV 配置状态")
    @PutMapping("/pv/products/{id}/status")
    public CommonResult<Boolean> updateProductPvStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(bonusEngineConfigService.updateProductPvStatus(id, status));
    }

    @Operation(summary = "删除商品 PV 配置")
    @DeleteMapping("/pv/products/{id}")
    public CommonResult<Boolean> deleteProductPvConfig(@PathVariable Long id) {
        return CommonResult.success(bonusEngineConfigService.deleteProductPvConfig(id));
    }

    @Operation(summary = "查询订单 PV 明细")
    @GetMapping("/pv/orders/{orderId}")
    public CommonResult<List<DmsOrderPvDetail>> listOrderPvDetails(@PathVariable Long orderId) {
        return CommonResult.success(bonusEngineConfigService.listOrderPvDetails(orderId));
    }

    @Operation(summary = "查询奖金计算快照")
    @GetMapping("/snapshots/orders/{orderId}")
    public CommonResult<List<DmsBonusCalculationSnapshot>> listCalculationSnapshots(@PathVariable Long orderId) {
        return CommonResult.success(bonusEngineConfigService.listCalculationSnapshots(orderId));
    }

    @Operation(summary = "奖金规则模拟")
    @PostMapping("/simulate")
    public CommonResult<BonusSimulationVO> simulate(@Valid @RequestBody BonusSimulationDTO dto) {
        return CommonResult.success(bonusEngineConfigService.simulate(dto));
    }
}
