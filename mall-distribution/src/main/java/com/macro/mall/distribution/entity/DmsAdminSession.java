package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsAdminSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long adminId;

    private String username;

    private String token;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
