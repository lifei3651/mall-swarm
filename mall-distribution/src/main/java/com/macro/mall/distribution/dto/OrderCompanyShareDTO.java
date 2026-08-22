package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderCompanyShareDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "公司账户不能为空")
    private Long accountId;

    @Size(max = 128, message = "账户名称不能超过128个字")
    private String accountName;

    @DecimalMin(value = "0", message = "分账比例不能小于0")
    @DecimalMax(value = "1", message = "分账比例不能大于1")
    @Digits(integer = 1, fraction = 6, message = "分账比例格式不正确")
    private BigDecimal shareRate;

    @DecimalMin(value = "0", message = "分账金额不能小于0")
    @Digits(integer = 14, fraction = 2, message = "分账金额格式不正确")
    private BigDecimal shareAmount;

    @Size(max = 500, message = "备注不能超过500个字")
    private String remark;
}
