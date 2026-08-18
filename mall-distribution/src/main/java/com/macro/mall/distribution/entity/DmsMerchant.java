package com.macro.mall.distribution.entity;

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
    private Integer status;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
