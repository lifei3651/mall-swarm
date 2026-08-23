package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class LiveAnchorSaveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入已注册的商城手机号或登录账号")
    @Size(max = 64, message = "商城账号不能超过64个字符")
    private String memberAccount;

    @NotBlank(message = "主播展示名称不能为空")
    @Size(max = 60, message = "主播展示名称不能超过60个字")
    private String displayName;

    @NotBlank(message = "请选择直播账号类型")
    @Pattern(regexp = "PRODUCT|PLATFORM|FACTORY", message = "直播账号类型不正确")
    private String anchorType;

    @Size(max = 120, message = "厂家或机构名称不能超过120个字")
    private String companyName;

    @Size(max = 300, message = "主播简介不能超过300个字")
    private String bio;
}
