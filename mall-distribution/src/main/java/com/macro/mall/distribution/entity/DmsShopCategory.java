package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过64个字")
    private String categoryName;

    @Size(max = 2048, message = "分类图片地址不能超过2048个字符")
    private String iconUrl;

    private Integer sort;

    private Integer status;

    /** 是否在首页展示：1-展示，0-隐藏 */
    private Integer showOnHome;

    @Size(max = 256, message = "分类备注不能超过256个字")
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
