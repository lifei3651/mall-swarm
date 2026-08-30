package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopMember;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WeChatMiniProgramLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean phoneAuthorizationRequired;

    private boolean newMember;

    /** 小程序原生请求使用的商城会话；微信 session_key 永不返回前端。 */
    private String accessToken;

    private LocalDateTime expireTime;

    private DmsShopMember member;
}
