package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.LiveRoomSaveDTO;
import com.macro.mall.distribution.dto.LiveAnchorSaveDTO;
import com.macro.mall.distribution.dto.LiveCommentSubmitDTO;
import com.macro.mall.distribution.dto.LiveEngagementDTO;
import com.macro.mall.distribution.entity.DmsLiveComment;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.LiveAnalyticsVO;
import com.macro.mall.distribution.vo.LiveAnchorVO;
import com.macro.mall.distribution.vo.LiveRoomVO;
import com.macro.mall.distribution.vo.LiveStreamCredentialVO;
import com.macro.mall.distribution.vo.LiveStudioVO;

import java.util.List;

public interface LiveRoomService {

    List<LiveRoomVO> listPublic(int limit);

    List<LiveRoomVO> listPublic(Long tenantId, int limit);

    LiveRoomVO getPublic(Long id);

    List<LiveRoomVO> listAdmin(Integer status);

    LiveRoomVO save(Long id, LiveRoomSaveDTO dto);

    boolean updateStatus(Long id, Integer status);

    List<LiveAnchorVO> listAnchors(Integer status);

    LiveAnchorVO saveAnchor(Long id, LiveAnchorSaveDTO dto);

    boolean updateAnchorStatus(Long id, Integer status);

    LiveStudioVO getStudio(DmsShopMember member);

    LiveStreamCredentialVO start(Long roomId, DmsShopMember member);

    boolean stop(Long roomId, DmsShopMember member);

    boolean forceStop(Long roomId, String reason);

    List<DmsLiveComment> listComments(Long roomId, Long afterId, int limit);

    List<DmsLiveComment> listAdminComments(Long roomId, Integer status, int limit);

    DmsLiveComment submitComment(Long roomId, DmsShopMember member, LiveCommentSubmitDTO dto);

    boolean updateCommentStatus(Long commentId, Integer status);

    boolean recordEngagement(Long roomId, DmsShopMember member, LiveEngagementDTO dto);

    LiveAnalyticsVO getAnalytics(Long roomId);

    Long resolveRecentAttribution(Long tenantId, Long userId, List<Long> productIds);
}
