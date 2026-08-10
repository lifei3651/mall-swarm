package com.macro.mall.distribution.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.macro.mall.distribution.entity.DmsShopMember;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopAuthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 只用于服务端写入 HttpOnly Cookie，禁止进入浏览器可读取的响应体。 */
    @JsonIgnore
    private String token;

    private LocalDateTime expireTime;

    private DmsShopMember member;
}
