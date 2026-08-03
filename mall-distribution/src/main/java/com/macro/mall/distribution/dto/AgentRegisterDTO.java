package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 代理注册DTO
 */
@Data
public class AgentRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 代理名称 */
    private String agentName;

    /** 手机号 */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 身份证号 */
    private String idCard;

    /** 邀请码（上级的邀请码） */
    private String inviteCode;

    /** 来源类型 */
    private Integer sourceType;

    /** 创建推广身份时的初始卡级，后台开通/外部平移可指定 */
    private Integer initialLevel;

    /** 创建推广身份的原因，写入会员变更日志 */
    private String reason;
}
