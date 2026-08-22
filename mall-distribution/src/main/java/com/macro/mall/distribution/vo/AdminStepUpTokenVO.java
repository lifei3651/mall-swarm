package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminStepUpTokenVO {
    private String token;
    private long expiresInSeconds;
}
