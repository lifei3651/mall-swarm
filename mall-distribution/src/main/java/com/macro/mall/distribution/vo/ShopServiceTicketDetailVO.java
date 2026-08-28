package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopServiceTicket;
import com.macro.mall.distribution.entity.DmsShopServiceTicketReply;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShopServiceTicketDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private DmsShopServiceTicket ticket;
    private List<DmsShopServiceTicketReply> replies;
}
