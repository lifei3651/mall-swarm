package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ShopNicknameUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入昵称")
    @Size(min = 2, max = 20, message = "昵称需为2至20个字符")
    @Pattern(regexp = "^[\\p{L}\\p{N}_·.-]+$", message = "昵称仅支持中文、字母、数字及 _ · . -")
    private String nickname;
}
