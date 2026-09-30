package com.macro.mall.distribution.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DirectReferralConfigVO {
    private Boolean enabled;
    private BigDecimal commissionRate;
    private String purchaseScope;
    private Integer settlementDelayDays;
    private Long versionId;
    private String currentPolicyCode;
    private String versionName;
    private LocalDateTime effectiveTime;
    /** 历史或客户自定义制度只能展示；显式确认切换后才可另建规则。 */
    private Boolean readOnly;
}
