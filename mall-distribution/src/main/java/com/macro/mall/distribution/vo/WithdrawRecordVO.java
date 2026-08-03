package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现记录VO
 */
@Data
public class WithdrawRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 提现单号 */
    private String withdrawNo;

    /** 代理ID */
    private Long agentId;

    private String agentName;

    private String memberAccount;

    private String memberPhone;

    /** 提现金额 */
    private BigDecimal withdrawAmount;

    /** 提现方式 */
    private Integer withdrawType;

    /** 提现方式名称 */
    private String withdrawTypeName;

    /** 银行名称 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 账户姓名 */
    private String accountName;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核备注 */
    private String auditRemark;

    /** 打款时间 */
    private LocalDateTime payTime;

    private String payNo;

    /** 创建时间 */
    private LocalDateTime createTime;
}
