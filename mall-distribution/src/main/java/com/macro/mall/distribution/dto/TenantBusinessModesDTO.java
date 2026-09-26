package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 商城业务模式的最小读写合同。
 *
 * <p>该对象故意不包含品牌、主体、状态等租户资料，避免仅拥有奖金配置权限的账号
 * 通过业务模式页面批量修改整个租户。</p>
 */
@Data
public class TenantBusinessModesDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仅用于读取响应；更新时以路径中的 tenantId 为准。 */
    private Long id;

    @NotNull(message = "请选择推广资格开通方式")
    @Pattern(regexp = "DISABLED|AUTO_ON_INVITE|MANUAL_REVIEW|FIRST_PAID_ORDER", message = "推广资格开通方式不正确")
    private String promotionJoinMode;

    @NotNull(message = "请选择是否开启秒杀专区")
    @Min(value = 0, message = "秒杀专区状态不正确")
    @Max(value = 1, message = "秒杀专区状态不正确")
    private Integer flashSaleEnabled;

    @NotNull(message = "请选择秒杀奖金处理方式")
    @Pattern(regexp = "NONE|STANDARD", message = "秒杀奖金模式不正确")
    private String flashSaleBonusMode;

    @NotNull(message = "请选择是否开启复购区")
    @Min(value = 0, message = "复购区状态不正确")
    @Max(value = 1, message = "复购区状态不正确")
    private Integer repurchaseMallEnabled;

    /** 缺省时沿用已有状态，兼容升级前的后台客户端。 */
    @Min(value = 0, message = "优惠券状态不正确")
    @Max(value = 1, message = "优惠券状态不正确")
    private Integer couponEnabled;

    /** 缺省沿用旧值；仅控制新增余额支付、转账和人工调账，不影响存量清偿。 */
    @Min(value = 0, message = "余额交易状态不正确")
    @Max(value = 1, message = "余额交易状态不正确")
    private Integer balanceTransactionsEnabled;

    /** 缺省沿用旧值；关闭只阻止新多商户经营，不删除历史商户账务。 */
    @Min(value = 0, message = "多商户状态不正确")
    @Max(value = 1, message = "多商户状态不正确")
    private Integer multiMerchantEnabled;

    @NotNull(message = "请选择复购区进入资格")
    @Pattern(regexp = "PAID_MEMBER|AGENT|ALL_MEMBER", message = "复购区准入模式不正确")
    private String repurchaseEligibilityMode;

    @NotNull(message = "请选择复购奖金处理方式")
    @Pattern(regexp = "NONE|STANDARD", message = "复购奖金模式不正确")
    private String repurchaseBonusMode;
}
