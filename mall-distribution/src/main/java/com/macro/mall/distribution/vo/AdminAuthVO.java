package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsAdminUser;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminAuthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private LocalDateTime expireTime;

    private DmsAdminUser admin;

    private List<String> permissions;
}
