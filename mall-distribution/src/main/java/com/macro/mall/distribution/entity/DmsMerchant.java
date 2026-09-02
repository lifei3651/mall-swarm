package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMerchant implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    @Size(max = 64, message = "商户编号不能超过64个字符")
    private String merchantNo;
    @NotBlank(message = "商户名称不能为空")
    @Size(max = 128, message = "商户名称不能超过128个字符")
    private String merchantName;
    @Size(max = 64, message = "联系人不能超过64个字符")
    private String contactName;
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;
    @Size(max = 128, message = "经营主体不能超过128个字符")
    private String legalEntityName;
    @Size(max = 32, message = "统一社会信用代码不能超过32个字符")
    private String unifiedSocialCreditCode;
    @Size(max = 128, message = "收款户名不能超过128个字符")
    private String bankAccountName;
    @Size(max = 128, message = "开户银行不能超过128个字符")
    private String bankName;
    @Size(max = 64, message = "银行账号不能超过64个字符")
    private String bankAccountNo;
    @Size(max = 128, message = "发票抬头不能超过128个字符")
    private String invoiceTitle;
    @Size(max = 32, message = "纳税人识别号不能超过32个字符")
    private String taxpayerIdentificationNo;
    /** PENDING=待签约，SIGNED=已签约，EXPIRED=已终止。 */
    private String contractStatus;
    /** 平台要求商户持续维持的保证金目标。 */
    private BigDecimal requiredDepositAmount;
    /** 收款及结算资料版本；提现申请会保存当时的资料快照。 */
    private Integer profileVersion;
    private String settlementMode;
    /** 客户售后窗口结束后，商户货款继续等待的天数。 */
    private Integer defaultSettlementDays;
    /** 商户工作台账号是否允许登录：ENABLED / DISABLED。 */
    private String accountStatus;
    /** 是否允许新增成交和维护商品：ACTIVE / SUSPENDED / CLOSED。 */
    private String businessStatus;
    /** 历史订单履约能力：ENABLED / PLATFORM_ONLY / DISABLED。 */
    private String fulfillmentStatus;
    /** 是否允许提交新的提现申请：ENABLED / FROZEN。 */
    private String withdrawalStatus;
    /** 到期货款是否允许从待结算释放：ENABLED / FROZEN。 */
    private String settlementStatus;
    /** 保证金风控状态：NORMAL / FROZEN；不足状态由应缴与已缴金额动态计算。 */
    private String depositStatus;
    /** 平台准入审核：PENDING / APPROVED / REJECTED。 */
    private String auditStatus;
    /** 退出流程：NORMAL / EXITING / EXITED。 */
    private String exitStatus;
    private Integer status;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 以下三个字段只在平台刚开通商户时返回一次，不持久化。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String onboardingUsername;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String temporaryPassword;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime credentialExpiresAt;
}
