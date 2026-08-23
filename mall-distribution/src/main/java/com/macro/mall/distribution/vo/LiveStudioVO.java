package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class LiveStudioVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private LiveAnchorVO anchor;
    private List<LiveRoomVO> rooms;
    private boolean canStart;
    private String statusMessage;
}
