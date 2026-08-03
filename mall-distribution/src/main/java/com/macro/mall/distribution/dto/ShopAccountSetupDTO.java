package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import java.io.Serializable;

@Data
public class ShopAccountSetupDTO implements Serializable {
    private String username;
    @ToString.Exclude
    private String password;
}
