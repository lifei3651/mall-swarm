package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class AdminUserSaveDTO {

    private Long id;

    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,31}$", message = "后台账号需为4至32位，必须以英文字母开头且仅支持字母、数字和下划线")
    private String username;

    @ToString.Exclude
    @Size(min = 8, max = 64, message = "后台密码需要8至64位")
    private String password;

    @Size(max = 64, message = "后台名称不能超过64个字")
    private String nickname;

    @Size(max = 32, message = "角色编码不能超过32个字符")
    private String roleCode;

    @Size(max = 64, message = "单次最多设置64项权限")
    private List<String> permissions;

    private Integer status;
}
