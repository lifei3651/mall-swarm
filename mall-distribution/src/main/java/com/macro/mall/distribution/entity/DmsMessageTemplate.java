package com.macro.mall.distribution.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMessageTemplate implements Serializable {
    private Long id;
    private Long tenantId;
    private String eventType;
    private String category;
    @NotBlank @Size(max = 128) private String titleTemplate;
    @NotBlank @Size(max = 300) private String summaryTemplate;
    @NotBlank @Size(max = 1000) private String contentTemplate;
    private Integer enabled;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
