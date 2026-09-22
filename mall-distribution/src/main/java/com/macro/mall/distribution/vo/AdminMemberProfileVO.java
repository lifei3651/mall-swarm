package com.macro.mall.distribution.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台会员详情的最小披露视图。
 *
 * <p>不要在这里直接嵌入代理、账户或迁移实体：这些实体包含身份证、银行卡、内部账户主键等
 * 不属于“查看商城会员”权限的数据。跨域数据只有在调用方具备对应权限时才装配。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminMemberProfileVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Member member;
    private Agent agent;
    private Commission account;
    private Boolean canViewTeamPerformance;
    private PerformanceOverviewVO performance;
    private List<AssetAccount> assetAccounts;
    private List<Address> addresses;
    private List<ShopOrderVO> orders;
    private MigrationBaseline migrationBaseline;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Member implements Serializable {
        private Long id;
        private Long userId;
        private String phone;
        private String username;
        private String nickname;
        private String avatarUrl;
        private Long inviterId;
        private Integer status;
        private LocalDateTime lockTime;
        private LocalDateTime lastLoginTime;
        private LocalDateTime createTime;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Agent implements Serializable {
        private Long id;
        private String agentCode;
        private String agentName;
        private Integer agentLevel;
        private Long parentId;
        private Integer status;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Commission implements Serializable {
        private BigDecimal totalCommission;
        private BigDecimal settledCommission;
        private BigDecimal unsettledCommission;
        private BigDecimal frozenCommission;
        private Integer totalOrders;
        private Integer totalTeamMembers;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AssetAccount implements Serializable {
        private String assetCode;
        private String assetName;
        private BigDecimal balance;
        private BigDecimal withdrawableBalance;
        private BigDecimal frozenBalance;
        private BigDecimal totalIn;
        private BigDecimal totalOut;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address implements Serializable {
        private Long id;
        private String receiverName;
        private String receiverPhone;
        private String province;
        private String city;
        private String district;
        private String detailAddress;
        private Integer isDefault;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MigrationBaseline implements Serializable {
        private String batchNo;
        private String externalMemberCode;
        private Integer historicalOrderCount;
        private BigDecimal historicalPersonalPerformance;
        private BigDecimal historicalTeamPerformance;
        private Integer initialLevel;
        private LocalDateTime cutoverTime;
    }
}
