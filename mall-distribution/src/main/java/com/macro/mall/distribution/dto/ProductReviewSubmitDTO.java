package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProductReviewSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer rating;
    private String content;
}
