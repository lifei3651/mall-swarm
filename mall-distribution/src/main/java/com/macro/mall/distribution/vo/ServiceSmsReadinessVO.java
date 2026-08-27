package com.macro.mall.distribution.vo;

import lombok.Data;

import java.util.List;

@Data
public class ServiceSmsReadinessVO {
    private boolean readyForMemberOptIn;
    private int approvedTemplateCount;
    private int requiredTemplateCount;
    private int enabledEventCount;
    private int configuredBudgetCount;
    private int requiredBudgetCount;
    private int activeAuthorizationCount;
    private List<Item> items;

    public record Item(String code, String label, boolean passed, String detail) { }
}
