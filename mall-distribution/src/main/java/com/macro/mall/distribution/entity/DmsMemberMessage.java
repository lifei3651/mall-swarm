package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMemberMessage implements Serializable {
    private Long id;
    @JsonIgnore
    private Long tenantId;
    @JsonIgnore
    private Long memberId;
    @JsonIgnore
    private Long userId;
    @JsonIgnore
    private String eventKey;
    private String eventType;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String targetType;
    private Long targetId;
    private Long targetParentId;
    private Integer isRead;
    private LocalDateTime readTime;
    private LocalDateTime occurredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
