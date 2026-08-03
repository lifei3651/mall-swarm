package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.api.CommonPage;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dto.AdminAssetChangeDTO;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.BalanceFlowVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

@Tag(name = "MemberAssetController", description = "会员余额")
@RestController
@RequestMapping("/distribution/assets")
@RequiredArgsConstructor
public class MemberAssetController {

    private final MemberAssetService assetService;
    private final AdminAuthService adminAuthService;

    @Operation(summary = "查询会员余额")
    @GetMapping("/accounts")
    public CommonResult<List<DmsMemberAssetAccount>> listAccounts(@RequestParam(required = false) Long agentId,
                                                                  @RequestParam(required = false) Long userId) {
        return CommonResult.success(assetService.listAccounts(agentId, userId));
    }

    @Operation(summary = "查询会员余额流水")
    @GetMapping("/flows")
    public CommonResult<List<DmsMemberAssetFlow>> listFlows(@RequestParam(required = false) Long agentId,
                                                            @RequestParam(required = false) Long userId) {
        return CommonResult.success(assetService.listFlows(agentId, userId));
    }

    @Operation(summary = "分页查询全部会员余额流水")
    @GetMapping("/flow-records")
    public CommonResult<CommonPage<BalanceFlowVO>> searchFlows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(
                assetService.searchBalanceFlows(keyword, direction, sourceType, startTime, endTime)));
    }

    @Operation(summary = "后台人工增加会员余额（立即生效）")
    @PostMapping("/issue")
    public CommonResult<DmsMemberAssetFlow> issue(@RequestBody AdminAssetChangeDTO dto) {
        validateManualChange(dto);
        adminAuthService.verifyPassword(AdminContext.get(), dto.getAdminPassword());
        return CommonResult.success(assetService.issue(dto), "余额已增加");
    }

    @Operation(summary = "后台人工扣减会员余额（立即生效）")
    @PostMapping("/deduct")
    public CommonResult<DmsMemberAssetFlow> deduct(@RequestBody AdminAssetChangeDTO dto) {
        validateManualChange(dto);
        adminAuthService.verifyPassword(AdminContext.get(), dto.getAdminPassword());
        return CommonResult.success(assetService.deduct(dto), "余额已扣减");
    }

    private void validateManualChange(AssetChangeDTO dto) {
        if (dto == null || dto.getRemark() == null || dto.getRemark().isBlank()) {
            Asserts.fail("余额调整必须填写原因");
        }
        if (dto.getRequestId() == null
                || !dto.getRequestId().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")) {
            Asserts.fail("余额调整请求号无效，请关闭窗口后重试");
        }
    }
}
