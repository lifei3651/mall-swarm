package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageRecipientAuthorization;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface DmsMessageRecipientAuthorizationDao {
    DmsMessageRecipientAuthorization selectByMemberChannel(@Param("tenantId") Long tenantId,
                                                            @Param("memberId") Long memberId,
                                                            @Param("channel") String channel);
    DmsMessageRecipientAuthorization selectActive(@Param("tenantId") Long tenantId,
                                                   @Param("memberId") Long memberId,
                                                   @Param("channel") String channel,
                                                   @Param("now") LocalDateTime now);
    int insert(DmsMessageRecipientAuthorization authorization);
    int updatePreference(DmsMessageRecipientAuthorization authorization);
}
