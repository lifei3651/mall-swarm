package com.macro.mall.distribution.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** 团队 H5 提现累计风控；客户交付时可配置，但任何部署都保留明确上限。 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "shop.withdrawal")
public class WithdrawalLimitProperties {

    @Min(1)
    private int dailyMaxCount = 5;

    @Min(1)
    private int monthlyMaxCount = 50;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal dailyMaxAmount = new BigDecimal("100000.00");

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal monthlyMaxAmount = new BigDecimal("1000000.00");
}
