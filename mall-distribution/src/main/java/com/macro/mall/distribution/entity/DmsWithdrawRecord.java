package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现记录实体类
 * 对应数据库表：dms_withdraw_record
 */
@Data
@Schema(description = "提现记录信息")
public class DmsWithdrawRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @Schema(description = "id")
    private Long id;

    /** 提现单号 */
    @Schema(description = "withdrawNo")
    private String withdrawNo;

    /** 代理ID */
    @Schema(description = "agentId")
    private Long agentId;

    /** 用户ID */
    @Schema(description = "userId")
    private Long userId;

    /** 提现金额 */
    @Schema(description = "withdrawAmount")
    private BigDecimal withdrawAmount;

    /**
     * 提现方式
     * 1-银行卡 2-微信 3-支付宝
     */
    @Schema(description = "withdrawType")
    private Integer withdrawType;

    /** 银行名称 */
    @Schema(description = "bankName")
    private String bankName;

    /** 银行账号 */
    @Schema(description = "bankAccount")
    private String bankAccount;

    /** 账户姓名 */
    @Schema(description = "accountName")
    private String accountName;

    /**
     * 状态
     * 0-待审核 1-审核通过 2-打款中 3-打款成功 4-审核拒绝
     */
    @Schema(description = "status")
    private Integer status;

    /** 审核人ID */
    @Schema(description = "auditUserId")
    private Long auditUserId;

    /** 审核时间 */
    @Schema(description = "auditTime")
    private LocalDateTime auditTime;

    /** 审核备注 */
    @Schema(description = "auditRemark")
    private String auditRemark;

    /** 打款时间 */
    @Schema(description = "payTime")
    private LocalDateTime payTime;

    /** 打款流水号 */
    @Schema(description = "payNo")
    private String payNo;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "updateTime")
    private LocalDateTime updateTime;
}
