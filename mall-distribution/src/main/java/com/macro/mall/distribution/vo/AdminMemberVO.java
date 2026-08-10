package com.macro.mall.distribution.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台会员列表聚合信息；不包含密码等敏感字段。 */
@Data
public class AdminMemberVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String memberAccount;
    private String phone;
    /** 登录账号；字段名保留 username 以兼容现有管理端接口。 */
    private String username;
    private String nickname;
    private String inviteCode;
    private Integer status;
    private Boolean loginLocked;
    /** 支付密码连续错误造成的锁定；不暴露支付密码或哈希。 */
    private Boolean paymentPasswordLocked;
    /** 仅供服务端判断30分钟锁定是否仍生效，不返回管理端。 */
    @JsonIgnore
    private LocalDateTime paymentPasswordLockTime;
    private LocalDateTime lockTime;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;

    /** 当前直属邀请人；后台移线执行成功后会同步为新的直属上级。 */
    private Long inviterUserId;
    private String inviterMemberAccount;
    private String inviterName;
    private String inviterPhone;

    private Boolean promotionActivated;
    private Long agentId;
    private String agentCode;
    private Integer agentLevel;
    private String agentLevelName;
    private Integer agentStatus;
    private String parentName;
    /** 是否存在尚未处理或尚未生效的历史移线申请。 */
    private Boolean hasPendingLineChange;
    private BigDecimal availableBalance;
    private BigDecimal unsettledCommission;
    /** 本人及不限层团队累计有效商品业绩，包含外部平移期初业绩。 */
    private BigDecimal teamPerformance;
    private Integer totalOrders;
}
