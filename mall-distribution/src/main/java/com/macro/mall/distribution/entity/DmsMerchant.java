package com.macro.mall.distribution.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMerchant implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    @Size(max = 64, message = "商户编号不能超过64个字符")
    private String merchantNo;
    @NotBlank(message = "商户名称不能为空")
    @Size(max = 128, message = "商户名称不能超过128个字符")
    private String merchantName;
    @Size(max = 64, message = "联系人不能超过64个字符")
    private String contactName;
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;
    private String settlementMode;
    private Integer status;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
