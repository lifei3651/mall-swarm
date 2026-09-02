package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AdminTemporaryCredentialVO {
    private String username;
    private String temporaryPassword;
    private LocalDateTime expiresAt;
}
