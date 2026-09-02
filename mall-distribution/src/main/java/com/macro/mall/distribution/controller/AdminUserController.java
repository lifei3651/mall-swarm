package com.macro.mall.distribution.controller;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.dto.AdminTemporaryCredentialDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.service.AdminUserService;
import com.macro.mall.distribution.vo.AdminTemporaryCredentialVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AdminUserController", description = "后台账号管理")
@RestController
@RequestMapping("/distribution/admin-users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "查询后台账号")
    @GetMapping
    public CommonResult<CommonPage<DmsAdminUser>> listUsers(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(adminUserService.listUsers(keyword, status)));
    }

    @Operation(summary = "保存后台账号")
    @PostMapping
    public CommonResult<DmsAdminUser> saveUser(@Valid @RequestBody AdminUserSaveDTO dto) {
        return CommonResult.success(adminUserService.saveUser(dto));
    }

    @Operation(summary = "更新后台账号")
    @PutMapping("/{id}")
    public CommonResult<DmsAdminUser> updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserSaveDTO dto) {
        dto.setId(id);
        return CommonResult.success(adminUserService.saveUser(dto));
    }

    @Operation(summary = "重置后台账号密码")
    @PutMapping("/{id}/password")
    public CommonResult<Boolean> updatePassword(@PathVariable Long id, @Valid @RequestBody AdminPasswordDTO dto) {
        return CommonResult.success(adminUserService.updatePassword(id, dto));
    }

    @Operation(summary = "生成24小时有效且首次登录必须修改的一次性临时凭据")
    @PostMapping("/{id}/temporary-credential")
    public CommonResult<AdminTemporaryCredentialVO> issueTemporaryCredential(
            @PathVariable Long id, @Valid @RequestBody AdminTemporaryCredentialDTO dto) {
        return CommonResult.success(adminUserService.issueTemporaryCredential(id, dto));
    }

    @Operation(summary = "解除后台账号密码错误锁定")
    @PutMapping("/{id}/unlock")
    public CommonResult<Boolean> unlock(@PathVariable Long id) {
        return CommonResult.success(adminUserService.unlock(id));
    }

    @Operation(summary = "更新后台账号状态")
    @PutMapping("/{id}/status")
    public CommonResult<Boolean> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(adminUserService.updateStatus(id, status));
    }

    @Operation(summary = "查询权限选项")
    @GetMapping("/permission-options")
    public CommonResult<List<Map<String, String>>> permissionOptions() {
        return CommonResult.success(adminUserService.permissionOptions());
    }

    @Operation(summary = "查询可绑定到后台账号的商户")
    @GetMapping("/merchant-options")
    public CommonResult<List<Map<String, Object>>> merchantOptions() {
        return CommonResult.success(adminUserService.merchantOptions());
    }
}
