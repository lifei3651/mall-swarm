package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@Data
public class PerformanceVisibilityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "请选择团队业绩可见状态")
    private Boolean teamPerformanceVisibleAll;
}
