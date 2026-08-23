package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsLiveRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsLiveRoomDao {

    DmsLiveRoom selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    DmsLiveRoom selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    List<DmsLiveRoom> selectAdminList(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    List<DmsLiveRoom> selectPublicList(@Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    int insert(DmsLiveRoom room);

    int update(DmsLiveRoom room);

    int updateStatus(@Param("tenantId") Long tenantId, @Param("id") Long id,
                     @Param("status") Integer status, @Param("expectedVersion") Integer expectedVersion);

    int deleteProducts(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId);

    int insertProduct(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                      @Param("productId") Long productId, @Param("sortOrder") Integer sortOrder);

    List<Long> selectProductIds(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId);
}
