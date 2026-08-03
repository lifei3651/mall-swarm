package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopMemberSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsShopMemberSessionDao {

    DmsShopMemberSession selectByToken(@Param("token") String token);

    int insert(DmsShopMemberSession session);

    int disableByToken(@Param("token") String token);

    int disableByMemberId(@Param("memberId") Long memberId);
}
