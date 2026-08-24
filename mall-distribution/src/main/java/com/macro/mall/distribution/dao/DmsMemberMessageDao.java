package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMemberMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import com.macro.mall.distribution.vo.MessageUnreadCountVO;

public interface DmsMemberMessageDao {
    int insertIgnore(DmsMemberMessage message);
    DmsMemberMessage selectOwned(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                                 @Param("id") Long id);
    List<DmsMemberMessage> selectPage(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                                      @Param("category") String category);
    List<MessageUnreadCountVO> countUnreadByCategory(@Param("tenantId") Long tenantId,
                                                      @Param("memberId") Long memberId);
    int markRead(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId, @Param("id") Long id);
    int markAllRead(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                    @Param("category") String category);
}
