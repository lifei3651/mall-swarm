package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopOrderServiceRemarkDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客服内部备注；传空字符串可清除。 */
    @Size(max = 500, message = "客服备注不能超过500个字")
    private String serviceRemark;
}
