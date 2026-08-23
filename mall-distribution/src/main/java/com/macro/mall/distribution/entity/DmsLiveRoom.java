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
    private Long anchorId;
    /** PRODUCT 厂家商品、PLATFORM 平台讲解、FACTORY 工厂常态。 */
    private String liveType;
    /** EXTERNAL 外部视频源、TENCENT 腾讯云直播。 */
    private String providerCode;
    /** 可公开保存的流标识，不包含任何推流鉴权信息。 */
    private String streamName;
    /** 仅保存公开观看或回放地址，禁止保存推流地址、鉴权密钥。 */
    private String watchUrl;
    private Integer commentEnabled;
    private Integer shareEnabled;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private String stopReason;
    /** 0草稿、1预告、2直播中、3已结束、4停用。 */
    private Integer status;
    private Integer viewerCount;
    private Integer heatCount;
    private Integer sortOrder;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
