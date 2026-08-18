package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopTrade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsShopTradeDao {
    DmsShopTrade selectById(@Param("id") Long id);
    DmsShopTrade selectByIdForUpdate(@Param("id") Long id);
    DmsShopTrade selectByTradeNo(@Param("tradeNo") String tradeNo);
    DmsShopTrade selectByTradeNoForUpdate(@Param("tradeNo") String tradeNo);
    int insert(DmsShopTrade trade);
    int markPaid(@Param("id") Long id, @Param("payType") String payType);
    int closePending(@Param("id") Long id);
}
