package com.macro.mall.distribution.dto;

import lombok.Data;

@Data
public class LineChangeAuditDTO {
    /** 1通过，2拒绝 */
    private Integer status;
    private String remark;
}
