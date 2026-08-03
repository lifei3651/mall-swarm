package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台关键操作日志
 */
@Data
public class DmsOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String moduleName;

    private String operationType;

    private String targetType;

    private String targetId;

    private Long operatorId;

    private String operatorName;

    private String beforeData;

    private String afterData;

    private String remark;

    private LocalDateTime createTime;
}
