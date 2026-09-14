package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提现申请DTO
 */
@Data
public class WithdrawApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long agentId;

    /** 商城用户ID；没有推广身份但规则允许余额持有人提现时使用。 */
    private Long userId;

    /** 提现金额 */
    private BigDecimal withdrawAmount;

    /** 提现方式 */
    private Integer withdrawType;

    /** 银行名称 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 账户姓名 */
    private String accountName;
}
