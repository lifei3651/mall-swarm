package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class AdminUserSaveDTO {

    private Long id;

    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,31}$", message = "后台账号需为4至32位，必须以英文字母开头且仅支持字母、数字和下划线")
    private String username;

    @ToString.Exclude
    @Size(min = 10, max = 64, message = "后台密码需要10至64位")
    private String password;

    /** 保存管理员账号前，对当前登录管理员进行二次身份确认。 */
    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String currentAdminPassword;

    @Size(max = 64, message = "后台名称不能超过64个字")
    private String nickname;

    @Size(max = 32, message = "角色编码不能超过32个字符")
    private String roleCode;

    @Size(max = 64, message = "单次最多设置64项权限")
    private List<String> permissions;

    /** 可选；绑定后账号只能维护该商户自己的商品。 */
    private Long merchantId;

    private Integer status;
}
