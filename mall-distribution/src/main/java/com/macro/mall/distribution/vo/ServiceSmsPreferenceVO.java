package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ServiceSmsPreferenceVO implements Serializable {
    private boolean available;
    private boolean enabled;
    private String maskedPhone;
    private String consentVersion;
    private LocalDateTime authorizedTime;
    private String statusText;
}
