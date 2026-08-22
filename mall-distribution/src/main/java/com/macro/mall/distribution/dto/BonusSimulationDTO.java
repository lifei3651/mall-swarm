package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BonusSimulationDTO {

    @Positive(message = "客户编号不正确")
    private Long tenantId;

    @Positive(message = "奖金规则版本编号不正确")
    private Long ruleVersionId;

    @Positive(message = "下单会员编号不正确")
    private Long orderUserId;

    /** 登录账号或手机号。 */
    @Size(max = 64, message = "会员登录账号或手机号不能超过64个字符")
    private String orderMemberKey;

    @Size(max = 64, message = "会员名称不能超过64个字符")
    private String orderUserName;

    @NotNull(message = "请输入订单金额")
    @DecimalMin(value = "0.01", message = "订单金额必须大于0")
    @DecimalMax(value = "9999999999.99", message = "订单金额超出系统支持范围")
    @Digits(integer = 10, fraction = 2, message = "订单金额最多保留2位小数")
    private BigDecimal orderAmount;
}
