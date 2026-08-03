package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 订单支付瞬间的组织归属快照，创建后不可修改。 */
@Data
public class DmsOrderRelationSnapshot implements Serializable {
    private Long id;
    private Long tenantId;
    private Long ruleVersionId;
    private Long orderId;
    private String orderNo;
    private Long orderUserId;
    private Long ownerAgentId;
    private Long targetAgentId;
    private Long targetUserId;
    private String targetAgentName;
    private Integer relationLevel;
    private String relationPath;
    private LocalDateTime snapshotTime;
}
