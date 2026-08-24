package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RealNameStatusVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean verified;
    private Boolean adult;
    private Boolean verificationAvailable;
    private String maskedRealName;
    private String maskedIdCard;
    private LocalDateTime verifiedTime;
}
