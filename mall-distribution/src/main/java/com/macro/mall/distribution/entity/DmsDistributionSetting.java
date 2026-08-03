package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsDistributionSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String settingKey;

    private String settingValue;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
