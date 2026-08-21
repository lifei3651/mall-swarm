package com.macro.mall.distribution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 代理更新DTO（限制可更新字段，防止批量赋值攻击）
 */
@Data
@Schema(description = "代理更新参数")
public class AgentUpdateDTO {

    @Schema(description = "代理名称")
    @Size(max = 64, message = "会员名称不能超过64个字符")
    private String agentName;

    @Schema(description = "真实姓名")
    @Size(max = 64, message = "真实姓名不能超过64个字符")
    private String realName;

    @Schema(description = "手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @Schema(description = "身份证号")
    @Pattern(regexp = "^(?:[1-9]\\d{14}|[1-9]\\d{16}[0-9Xx])$", message = "请输入正确的15位或18位身份证号")
    private String idCard;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
