package com.macro.mall.distribution.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class WeChatSubscriptionTemplateVO implements Serializable {
    private String eventType;
    private String templateId;
    private String title;
    private Integer availableGrants;
}
