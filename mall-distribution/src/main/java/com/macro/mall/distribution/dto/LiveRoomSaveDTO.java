package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LiveRoomSaveDTO implements Serializable {

    @NotBlank(message = "直播间标题不能为空")
    @Size(max = 80, message = "直播间标题不能超过80个字")
    private String title;

    @Size(max = 160, message = "直播间副标题不能超过160个字")
    private String subtitle;

    @NotBlank(message = "请上传直播封面")
    @Size(max = 2048, message = "直播封面地址不能超过2048个字符")
    private String coverUrl;

    @Size(max = 60, message = "主播名称不能超过60个字")
    private String anchorName;

    private Long anchorId;

    @Pattern(regexp = "PRODUCT|PLATFORM|FACTORY", message = "直播类型不正确")
    private String liveType;

    @Pattern(regexp = "EXTERNAL|TENCENT", message = "直播服务类型不正确")
    private String providerCode;

    @Size(max = 2048, message = "观看地址不能超过2048个字符")
    private String watchUrl;

    @Min(value = 0, message = "评论开关不正确")
    @Max(value = 1, message = "评论开关不正确")
    private Integer commentEnabled;

    @Min(value = 0, message = "分享开关不正确")
    @Max(value = 1, message = "分享开关不正确")
    private Integer shareEnabled;

    @NotNull(message = "请选择计划开播时间")
    private LocalDateTime scheduledStartTime;

    private LocalDateTime scheduledEndTime;

    @Min(value = 0, message = "直播状态不正确")
    @Max(value = 4, message = "直播状态不正确")
    private Integer status;

    @Min(value = 0, message = "观看人数不能小于0")
    private Integer viewerCount;

    @Min(value = 0, message = "热度不能小于0")
    private Integer heatCount;

    @Min(value = -9999, message = "排序值不能小于-9999")
    @Max(value = 9999, message = "排序值不能大于9999")
    private Integer sortOrder;

    @Size(max = 20, message = "单个直播间最多关联20个商品")
    private List<Long> productIds;
}
