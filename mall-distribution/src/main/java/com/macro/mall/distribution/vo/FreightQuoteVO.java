package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreightQuoteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品实付基数，不含运费，也是奖金/业绩基数。 */
    private BigDecimal productAmount;
    private BigDecimal freightAmount;
    private BigDecimal payAmount;
}
