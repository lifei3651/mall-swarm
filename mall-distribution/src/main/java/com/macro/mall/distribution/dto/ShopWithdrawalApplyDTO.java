package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ShopWithdrawalApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "请输入提现金额")
    @DecimalMin(value = "0.01", message = "提现金额必须大于0")
    private BigDecimal withdrawAmount;
    @NotNull(message = "请选择提现方式")
    @Min(value = 1, message = "提现方式不正确")
    @Max(value = 3, message = "提现方式不正确")
    private Integer withdrawType;
    @Size(max = 128, message = "收款渠道名称不能超过128个字")
    private String bankName;
    @Size(max = 128, message = "收款账号不能超过128个字")
    private String bankAccount;
    @NotBlank(message = "请填写收款人姓名")
    @Size(max = 64, message = "收款人姓名不能超过64个字")
    private String accountName;
    @NotBlank(message = "请输入支付密码")
    @Pattern(regexp = "^\\d{6}$", message = "支付密码必须是6位数字")
    @ToString.Exclude
    private String paymentPassword;
    @NotBlank(message = "请输入短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码必须是6位数字")
    @ToString.Exclude
    private String smsCode;
}
