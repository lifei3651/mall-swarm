package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsLiveRoom implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String title;
    private String subtitle;
    private String coverUrl;
    private String anchorName;
    /** 仅保存公开观看或回放地址，禁止保存推流地址、鉴权密钥。 */
    private String watchUrl;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    /** 0草稿、1预告、2直播中、3已结束、4停用。 */
    private Integer status;
    private Integer viewerCount;
    private Integer heatCount;
    private Integer sortOrder;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
