package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class FreightQuoteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 优惠前商品金额，不含运费；奖金使用订单项锁定的独立基数。 */
    private BigDecimal productAmount;
    private BigDecimal freightAmount;
    private BigDecimal payAmount;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private Long selectedCouponClaimId;
    private java.util.List<ShopCouponVO> coupons = java.util.List.of();

    public FreightQuoteVO(BigDecimal productAmount, BigDecimal freightAmount, BigDecimal payAmount) {
        this.productAmount=productAmount; this.freightAmount=freightAmount; this.payAmount=payAmount;
    }
}
