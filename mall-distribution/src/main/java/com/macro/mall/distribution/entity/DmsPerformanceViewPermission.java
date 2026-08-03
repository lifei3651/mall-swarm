package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsPerformanceViewPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long agentId;

    private Long userId;

    private String agentName;

    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
