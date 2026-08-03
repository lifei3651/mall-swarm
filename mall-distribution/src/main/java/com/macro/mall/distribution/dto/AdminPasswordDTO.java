package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class AdminPasswordDTO {

    @ToString.Exclude
    private String password;
}
