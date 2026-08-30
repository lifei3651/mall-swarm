package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单奖金全链路只读视图。
 *
 * <p>该视图不重新计算奖金，也不定义客户制度，只把订单支付时已经冻结的关系、
 * 奖金程序版本、实际奖金记录、钱包入账及退款追回证据串联起来。</p>
 */
@Data
public class OrderBonusTraceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private String status;
    private String statusName;
    private String explanation;

    private Long ruleVersionId;
    private String ruleVersionNo;
    private String ruleVersionName;

    private Long calculationTaskId;
    private Integer calculationTaskStatus;
    private String calculationTaskStatusName;
    private Integer calculationRetryCount;
    private String calculationFailReason;

    /** 奖金程序最初计算出的金额；优先读取不可变计算快照。 */
    private BigDecimal calculatedAmount = BigDecimal.ZERO;
    /** 仍在冷静期内、尚未进入余额的有效奖金。 */
    private BigDecimal pendingAmount = BigDecimal.ZERO;
    /** 已进入余额且扣除该订单退款追回后的净额。 */
    private BigDecimal settledNetAmount = BigDecimal.ZERO;
    /** 真实写入会员余额的累计金额。 */
    private BigDecimal walletIssuedAmount = BigDecimal.ZERO;
    /** 退款导致的累计应追回金额。 */
    private BigDecimal clawbackAmount = BigDecimal.ZERO;
    /** 已经从待结算、余额或可提现账户中冲减的金额。 */
    private BigDecimal deductedAmount = BigDecimal.ZERO;
    /** 尚未追回、等待未来奖金继续抵扣的金额。 */
    private BigDecimal debtAmount = BigDecimal.ZERO;
    /** 当前订单最终仍有效的奖金净额。 */
    private BigDecimal currentNetAmount = BigDecimal.ZERO;

    private Integer relationCount = 0;
    private Integer recipientCount = 0;

    private List<RelationNode> relationChain = new ArrayList<>();
    private List<CalculationEvidence> calculationEvidence = new ArrayList<>();
    /** 兼容并保留原有实际奖金记录，它只是完整链路中的一个阶段。 */
    private List<CommissionRecordVO> actualRecords = new ArrayList<>();
    private List<AssetFlow> assetFlows = new ArrayList<>();
    private List<Clawback> clawbacks = new ArrayList<>();
    private List<TimelineEvent> timeline = new ArrayList<>();

    @Data
    public static class RelationNode implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer relationLevel;
        private String memberAccount;
        private String memberName;
        private String relationPath;
        private LocalDateTime snapshotTime;
    }

    @Data
    public static class CalculationEvidence implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private BigDecimal totalPv;
        private BigDecimal totalBonus;
        private String riskStatus;
        private String riskStatusName;
        private LocalDateTime createTime;
    }

    @Data
    public static class AssetFlow implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Long commissionRecordId;
        private String recordNo;
        private String memberAccount;
        private String memberName;
        private String flowNo;
        private String action;
        private String actionName;
        private BigDecimal amount;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private String remark;
        private LocalDateTime createTime;
    }

    @Data
    public static class Clawback implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Long refundId;
        private Long commissionRecordId;
        private String recordNo;
        private String memberAccount;
        private String memberName;
        private BigDecimal originalAmount;
        private BigDecimal clawbackAmount;
        private BigDecimal deductedAmount;
        private BigDecimal debtAmount;
        private String typeName;
        private String statusName;
        private String reason;
        private LocalDateTime createTime;
    }

    @Data
    public static class TimelineEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        private String code;
        private String title;
        /** success、warning、danger、info，供后台统一显示。 */
        private String status;
        private String description;
        private LocalDateTime time;
    }
}
