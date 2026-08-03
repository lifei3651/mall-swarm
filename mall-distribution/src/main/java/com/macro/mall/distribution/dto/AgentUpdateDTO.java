package com.macro.mall.distribution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 代理更新DTO（限制可更新字段，防止批量赋值攻击）
 */
@Data
@Schema(description = "代理更新参数")
public class AgentUpdateDTO {

    @Schema(description = "代理名称")
    private String agentName;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "备注")
    private String remark;
}
