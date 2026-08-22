package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminStepUpDTO {

    @NotBlank(message = "请输入当前管理员密码")
    @Size(min = 8, max = 64, message = "管理员密码长度必须为8-64位")
    private String password;

    @NotBlank(message = "请求方法不能为空")
    @Pattern(regexp = "^(POST|PUT|DELETE)$", message = "请求方法无效")
    private String method;

    @NotBlank(message = "请求路径不能为空")
    @Size(max = 300, message = "请求路径过长")
    @Pattern(regexp = "^/(distribution|shop/admin)/[A-Za-z0-9_./-]+$", message = "请求路径无效")
    private String path;
}
