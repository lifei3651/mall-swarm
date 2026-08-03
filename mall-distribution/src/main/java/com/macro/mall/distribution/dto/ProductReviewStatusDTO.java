package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProductReviewStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0-隐藏，1-恢复展示。 */
    private Integer status;
    private String reason;
}
