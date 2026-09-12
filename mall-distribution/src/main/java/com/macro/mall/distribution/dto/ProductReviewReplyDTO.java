package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 回复身份由后台登录账号决定，不接受客户端指定商家或平台身份。 */
@Data
public class ProductReviewReplyDTO {
    @NotBlank
    @Size(max = 500)
    private String content;

    /** 每一方独立的编辑版本，防止覆盖其他员工刚刚保存的回复。 */
    @Min(0)
    private Integer expectedVersion;
}
