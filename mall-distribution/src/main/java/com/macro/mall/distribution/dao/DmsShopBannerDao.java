package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopBanner;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsShopBannerDao {

    DmsShopBanner selectById(@Param("id") Long id);

    List<DmsShopBanner> selectList(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    List<DmsShopBanner> selectActive(@Param("tenantId") Long tenantId);

    int insert(DmsShopBanner banner);

    int update(DmsShopBanner banner);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
