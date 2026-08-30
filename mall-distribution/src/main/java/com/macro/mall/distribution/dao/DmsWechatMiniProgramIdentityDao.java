package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsWechatMiniProgramIdentityDao {

    DmsWechatMiniProgramIdentity selectActive(@Param("tenantId") Long tenantId,
                                               @Param("appIdHash") String appIdHash,
                                               @Param("openIdHash") String openIdHash);

    DmsWechatMiniProgramIdentity selectByMember(@Param("tenantId") Long tenantId,
                                                 @Param("appIdHash") String appIdHash,
                                                 @Param("memberId") Long memberId);

    int insert(DmsWechatMiniProgramIdentity identity);

    int updateLogin(DmsWechatMiniProgramIdentity identity);
}
