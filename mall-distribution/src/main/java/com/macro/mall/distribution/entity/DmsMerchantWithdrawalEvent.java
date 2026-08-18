package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMerchantWithdrawalEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long merchantId;
    private Long withdrawalId;
    private String withdrawalNo;
    private String fromStatus;
    private String toStatus;
    private String remark;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
