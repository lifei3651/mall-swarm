package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsLiveAnchor;
import lombok.Data;

import java.io.Serializable;

@Data
public class LiveAnchorVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private DmsLiveAnchor anchor;
    private String memberAccount;
    private String statusLabel;
    private Integer liveRoomCount;
    private Integer liveRoomLiveCount;
}
