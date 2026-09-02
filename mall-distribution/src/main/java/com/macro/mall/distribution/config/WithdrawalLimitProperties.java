package com.macro.mall.distribution.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** 会员提现频次与单笔人工审核阈值；不使用日/月累计金额拦截正常提现。 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "shop.withdrawal")
public class WithdrawalLimitProperties {

    @Min(1)
    private int dailyMaxCount = 2;

    @Min(1)
    private int monthlyMaxCount = 10;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal manualReviewThreshold = new BigDecimal("1000.00");
}
