package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/** 平台为商户开通工作台时填写的最小资料与一次性初始凭据。 */
@Data
public class MerchantOnboardingDTO {
    @Size(max = 64, message = "商户编号不能超过64个字符")
    private String merchantNo;

    @NotBlank(message = "商户名称不能为空")
    @Size(max = 128, message = "商户名称不能超过128个字符")
    private String merchantName;

    @Size(max = 64, message = "联系人不能超过64个字符")
    private String contactName;

    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;

    @DecimalMin(value = "0", message = "应缴保证金不能小于0")
    private BigDecimal requiredDepositAmount;

    private Integer defaultSettlementDays;

    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,31}$", message = "商家账号需为4至32位，必须以英文字母开头且仅支持字母、数字和下划线")
    private String username;

    /** 兼容旧客户端；服务端会忽略此值并生成24小时一次性临时密码。 */
    @ToString.Exclude
    @Size(max = 64, message = "初始密码长度不正确")
    private String password;

    /** 创建商家账号前，对当前平台管理员进行身份确认。 */
    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String currentAdminPassword;
}
