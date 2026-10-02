package com.macro.mall.distribution.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
@Data
public class ShopInviteBindDTO {
    @NotBlank @Pattern(regexp="(?i)^[A-Z0-9]{8}$", message="邀请码格式不正确")
    private String inviteCode;
}
