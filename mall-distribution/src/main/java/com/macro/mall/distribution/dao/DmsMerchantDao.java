package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsMerchantDao {
    DmsMerchant selectById(@Param("id") Long id);
    DmsMerchant selectByNo(@Param("tenantId") Long tenantId, @Param("merchantNo") String merchantNo);
    List<DmsMerchant> selectList(@Param("tenantId") Long tenantId, @Param("keyword") String keyword,
                                 @Param("status") Integer status);
    int insert(DmsMerchant merchant);
    int update(DmsMerchant merchant);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
