package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 导入代理DTO
 */
@Data
public class ImportAgentDTO implements Serializable {

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

    /** 上级代理编号 */
    private String parentAgentCode;

    /** 银行名称 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 备注 */
    private String remark;
}
