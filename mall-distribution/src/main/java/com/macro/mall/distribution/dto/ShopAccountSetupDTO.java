package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

@Data
public class ShopAccountSetupDTO implements Serializable {
    @NotBlank(message = "请输入登录账号")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,19}$", message = "登录账号需为4至20位，必须以英文字母开头且仅支持字母、数字和下划线")
    private String username;
    @ToString.Exclude
    @NotBlank(message = "请输入登录密码")
    @Size(min = 10, max = 32, message = "登录密码需为10至32位")
    private String password;
}
