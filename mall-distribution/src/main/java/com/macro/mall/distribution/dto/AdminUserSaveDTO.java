package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
public class AdminUserSaveDTO {

    private Long id;

    private String username;

    @ToString.Exclude
    private String password;

    private String nickname;

    private String roleCode;

    private List<String> permissions;

    private Integer status;
}
