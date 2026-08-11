package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopNotice implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "公告标题不能超过128个字")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 1000, message = "公告内容不能超过1000个字")
    private String content;

    /** 公告类型：1-系统公告 2-活动公告 3-物流公告 */
    private Integer noticeType;

    private Integer sort;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
