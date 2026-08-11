package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

@Data
public class SmsCodeRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @Min(value = 1, message = "短信业务类型不正确")
    @Max(value = 9, message = "短信业务类型不正确")
    private Integer bizType;

    @ToString.Exclude
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码必须是6位数字")
    private String code;
}
