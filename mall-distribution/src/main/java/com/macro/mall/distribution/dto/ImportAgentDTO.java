package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

/**
 * 导入代理DTO
 */
@Data
public class ImportAgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;

    /** 代理名称 */
    @Size(max = 64, message = "会员名称不能超过64个字符")
    private String agentName;

    /** 手机号 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    /** 真实姓名 */
    @Size(max = 64, message = "真实姓名不能超过64个字符")
    private String realName;

    /** 身份证号 */
    @Pattern(regexp = "^(?:[1-9]\\d{14}|[1-9]\\d{16}[0-9Xx])$", message = "身份证号格式不正确")
    private String idCard;

    /** 上级代理编号 */
    @Size(max = 64, message = "上级会员编号不能超过64个字符")
    private String parentAgentCode;

    /** 银行名称 */
    @Size(max = 64, message = "银行名称不能超过64个字符")
    private String bankName;

    /** 银行账号 */
    @Size(max = 64, message = "银行账号不能超过64个字符")
    private String bankAccount;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
