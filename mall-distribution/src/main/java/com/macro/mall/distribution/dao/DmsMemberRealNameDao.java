package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMemberRealName;
import com.macro.mall.distribution.entity.DmsMemberRealNameAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface DmsMemberRealNameDao {
    DmsMemberRealName selectByMemberId(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId);
    int insert(DmsMemberRealName realName);
    int insertAttempt(DmsMemberRealNameAttempt attempt);
    long countAttemptsSince(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                            @Param("since") LocalDateTime since);
}
