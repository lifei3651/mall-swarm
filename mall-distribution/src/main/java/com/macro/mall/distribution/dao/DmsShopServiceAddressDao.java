package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopServiceAddressDao {

    DmsShopServiceAddress selectById(@Param("id") Long id);

    List<DmsShopServiceAddress> selectList(@Param("tenantId") Long tenantId,
                                           @Param("addressType") Integer addressType,
                                           @Param("status") Integer status);

    List<DmsShopServiceAddress> selectListForMerchant(@Param("tenantId") Long tenantId,
                                                      @Param("merchantId") Long merchantId,
                                                      @Param("addressType") Integer addressType,
                                                      @Param("status") Integer status);

    DmsShopServiceAddress selectDefault(@Param("tenantId") Long tenantId,
                                        @Param("addressType") Integer addressType);

    DmsShopServiceAddress selectDefaultForMerchant(@Param("tenantId") Long tenantId,
                                                    @Param("merchantId") Long merchantId,
                                                    @Param("addressType") Integer addressType);

    int insert(DmsShopServiceAddress address);

    int update(DmsShopServiceAddress address);

    int clearDefault(@Param("tenantId") Long tenantId,
                     @Param("addressType") Integer addressType);

    int clearDefaultForMerchant(@Param("tenantId") Long tenantId,
                                @Param("merchantId") Long merchantId,
                                @Param("addressType") Integer addressType);

    int updateStatus(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("status") Integer status);
}
