package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DistributionSettingsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean teamPerformanceVisibleAll;

    private Boolean directSalesMode;

    private List<PerformanceViewPermissionVO> permissions;
}
