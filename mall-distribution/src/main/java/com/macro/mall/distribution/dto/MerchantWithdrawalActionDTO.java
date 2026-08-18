package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class MerchantWithdrawalActionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String reason;
}
