package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopAddressDao {

    DmsShopAddress selectById(@Param("id") Long id);

    DmsShopAddress selectDefaultByMemberId(@Param("memberId") Long memberId);

    List<DmsShopAddress> selectByMemberId(@Param("memberId") Long memberId);

    int insert(DmsShopAddress address);

    int update(DmsShopAddress address);

    int clearDefault(@Param("memberId") Long memberId);

    int deleteById(@Param("id") Long id, @Param("memberId") Long memberId);
}
