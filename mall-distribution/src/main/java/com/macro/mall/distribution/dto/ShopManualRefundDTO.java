package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 后台超期退款申请。商品数量始终用于记录实际受影响的盒数，金额模式只改变商品款的计算方式。
 */
@Data
public class ShopManualRefundDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** QUANTITY 按选中盒数按比例退款；AMOUNT 使用后台填写的商品退款金额。 */
    @NotBlank(message = "请选择退款方式")
    @Pattern(regexp = "^(QUANTITY|AMOUNT)$", message = "退款方式不正确")
    private String refundMode;

    /** 金额模式下填写的商品退款金额，不含运费。 */
    private BigDecimal productRefundAmount;

    /** 选择本次退款涉及的商品及盒数。 */
    @Size(max = 200, message = "单次最多处理200项退款商品")
    private List<@Valid ShopAfterSaleItemDTO> items;

    @NotBlank(message = "请填写退款原因")
    @Size(max = 200, message = "退款原因不能超过200个字")
    private String reason;

    /** 兼容旧管理端字段；后台特殊退款一律由服务端归类为4，不信任客户端类型。 */
    private Integer applyType;

    private Long operatorId;

    @Size(max = 64, message = "操作人名称不能超过64个字")
    private String operatorName;
}
