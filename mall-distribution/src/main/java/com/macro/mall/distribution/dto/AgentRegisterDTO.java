package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

/**
 * 代理注册DTO
 */
@Data
public class AgentRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @NotNull(message = "请选择已有商城账号")
    @Positive(message = "商城账号编号不正确")
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
    @Pattern(regexp = "^(?:[1-9]\\d{14}|[1-9]\\d{16}[0-9Xx])$", message = "请输入正确的15位或18位身份证号")
    private String idCard;

    /** 邀请码（上级的邀请码） */
    @Size(max = 32, message = "邀请码长度不正确")
    private String inviteCode;

    /** 来源类型 */
    @Min(value = 1, message = "会员来源类型不正确")
    @Max(value = 4, message = "会员来源类型不正确")
    private Integer sourceType;

    /** 创建推广身份时的初始卡级，后台开通/外部平移可指定 */
    @Min(value = 1, message = "初始会员级别不正确")
    @Max(value = 8, message = "初始会员级别不正确")
    private Integer initialLevel;

    /** 创建推广身份的原因，写入会员变更日志 */
    @Size(max = 300, message = "开通原因不能超过300个字")
    private String reason;
}
