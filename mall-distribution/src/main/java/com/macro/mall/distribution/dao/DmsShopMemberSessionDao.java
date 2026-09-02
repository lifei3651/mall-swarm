package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopMemberSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopMemberSessionDao {

    DmsShopMemberSession selectByToken(@Param("token") String token);

    int insert(DmsShopMemberSession session);

    int disableByToken(@Param("token") String token);

    int disableByMemberId(@Param("memberId") Long memberId);

    int disableByMemberIdAndSurface(@Param("memberId") Long memberId, @Param("surface") String surface);

    List<DmsShopMemberSession> selectActiveByMemberId(@Param("memberId") Long memberId);
}
