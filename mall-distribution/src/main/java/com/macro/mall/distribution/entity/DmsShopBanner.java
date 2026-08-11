package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopBanner implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    @NotBlank(message = "轮播图标题不能为空")
    @Size(max = 64, message = "轮播图标题不能超过64个字")
    private String title;

    @NotBlank(message = "轮播图图片不能为空")
    @Size(max = 2048, message = "轮播图图片地址不能超过2048个字符")
    private String imageUrl;

    @Size(max = 32, message = "轮播图跳转类型不能超过32个字符")
    private String linkType;

    @Size(max = 2048, message = "轮播图跳转内容不能超过2048个字符")
    private String linkValue;

    private Integer sort;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Size(max = 200, message = "轮播图备注不能超过200个字")
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
