package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopInviteBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入邀请码")
    @Pattern(regexp = "^[A-Za-z0-9]{8}$", message = "请输入完整的8位邀请码")
    private String inviteCode;
}
