package com.macro.mall.distribution.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DmsErpSyncTask {
    private Long id;
    private String taskNo;
    private Long integrationId;
    private Long tenantId;
    private String providerCode;
    private String bizType;
    private String bizId;
    /** 0待处理、1成功、2失败待重试、3人工终止 */
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String requestSummary;
    private String responseSummary;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
