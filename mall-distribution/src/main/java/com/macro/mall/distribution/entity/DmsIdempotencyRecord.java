package com.macro.mall.distribution.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DmsIdempotencyRecord {
    private String requestKey;
    /** 0=处理中，1=成功。 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
