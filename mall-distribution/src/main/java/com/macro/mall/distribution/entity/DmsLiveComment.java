package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsLiveComment implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long liveRoomId;
    private Long userId;
    private String displayName;
    private String content;
    /** 1 公开、2 平台隐藏。 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
