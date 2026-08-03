package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopNotice;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsShopNoticeDao {

    DmsShopNotice selectById(@Param("id") Long id);

    List<DmsShopNotice> selectList(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    List<DmsShopNotice> selectActive(@Param("tenantId") Long tenantId);

    int insert(DmsShopNotice notice);

    int update(DmsShopNotice notice);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
