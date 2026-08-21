package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetChangeDTO {

    private Long agentId;

    private Long userId;

    @NotNull(message = "请输入资产数量")
    @DecimalMin(value = "0.01", message = "资产数量必须大于0")
    @Digits(integer = 12, fraction = 2, message = "资产数量最多保留2位小数")
    private BigDecimal amount;

    private String bizType;

    private String bizId;

    /** 客户端为高风险人工调账生成的唯一请求号，用于防止重复入账/扣款。 */
    @NotBlank(message = "缺少余额调整请求号")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            message = "余额调整请求号无效，请关闭窗口后重试")
    private String requestId;

    @NotBlank(message = "余额调整必须填写原因")
    @Size(max = 500, message = "余额调整原因不能超过500个字")
    private String remark;
}
