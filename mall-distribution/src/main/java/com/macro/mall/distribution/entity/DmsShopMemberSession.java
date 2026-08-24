package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopMemberSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long memberId;

    private Long userId;

    private String token;

    /** 会话签发来源：public、team、integrated；历史会话为 legacy。 */
    private String surface;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
