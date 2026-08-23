package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LiveStreamCredentialVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long roomId;
    private String providerCode;
    private String streamName;
    /** 仅返回给当前授权主播，公开直播接口永不返回。 */
    private String pushUrl;
    private String playbackUrl;
    private LocalDateTime expireTime;
    private String instructions;
}
