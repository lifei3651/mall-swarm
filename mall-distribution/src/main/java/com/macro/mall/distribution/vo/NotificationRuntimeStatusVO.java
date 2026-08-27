package com.macro.mall.distribution.vo;

import lombok.Data;

@Data
public class NotificationRuntimeStatusVO {
    private boolean externalEnabled;
    private boolean workerEnabled;
    private String smsStatus;
    private String appPushStatus;
    private String miniProgramStatus;
    private String budgetStatus;
    private String authorizationStatus;
    private ServiceSmsReadinessVO serviceSmsReadiness;
}
