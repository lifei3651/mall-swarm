package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class RealNameVerifyDTO {
    @NotBlank(message = "请输入真实姓名")
    @Size(max = 64, message = "姓名长度不正确")
    @ToString.Exclude
    private String realName;

    @NotBlank(message = "请输入身份证号")
    @Size(max = 18, message = "身份证号长度不正确")
    @ToString.Exclude
    private String idCard;

    @AssertTrue(message = "请先阅读并同意实名认证授权")
    private Boolean sensitiveInfoConsent;
}
