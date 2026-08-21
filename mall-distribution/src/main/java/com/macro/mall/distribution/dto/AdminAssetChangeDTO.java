package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 后台人工调账命令；敏感的二次认证密码只参与本次请求，不写入流水或日志。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAssetChangeDTO extends AssetChangeDTO {

    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String adminPassword;

    @AssertTrue(message = "请选择需要调整余额的会员")
    public boolean isMemberTargetSpecified() {
        return getAgentId() != null || getUserId() != null;
    }
}
