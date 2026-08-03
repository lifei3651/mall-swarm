package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopMember;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopAuthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private LocalDateTime expireTime;

    private DmsShopMember member;
}
