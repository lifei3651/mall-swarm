package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.entity.DmsLiveAnchor;
import com.macro.mall.distribution.entity.DmsLiveComment;
import com.macro.mall.distribution.vo.LiveAnalyticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsLiveRoomDao {

    DmsLiveRoom selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    DmsLiveRoom selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    DmsLiveRoom selectByStreamNameForUpdate(@Param("streamName") String streamName);

    List<DmsLiveRoom> selectAdminList(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    List<DmsLiveRoom> selectPublicList(@Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    List<DmsLiveRoom> selectByAnchorId(@Param("tenantId") Long tenantId, @Param("anchorId") Long anchorId);

    int insert(DmsLiveRoom room);

    int update(DmsLiveRoom room);

    int startRoom(@Param("tenantId") Long tenantId, @Param("id") Long id,
                  @Param("anchorId") Long anchorId, @Param("watchUrl") String watchUrl,
                  @Param("expectedVersion") Integer expectedVersion);

    int stopRoom(@Param("tenantId") Long tenantId, @Param("id") Long id,
                 @Param("status") Integer status, @Param("stopReason") String stopReason,
                 @Param("expectedVersion") Integer expectedVersion);

    int markStreamConnected(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int markStreamDisconnected(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int updateStatus(@Param("tenantId") Long tenantId, @Param("id") Long id,
                     @Param("status") Integer status, @Param("expectedVersion") Integer expectedVersion);

    int deleteProducts(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId);

    int insertProduct(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                      @Param("productId") Long productId, @Param("sortOrder") Integer sortOrder);

    List<Long> selectProductIds(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId);

    DmsLiveAnchor selectAnchorById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    DmsLiveAnchor selectAnchorByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    DmsLiveAnchor selectAnchorByMember(@Param("tenantId") Long tenantId, @Param("memberUserId") Long memberUserId);

    List<DmsLiveAnchor> selectAnchors(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    int insertAnchor(DmsLiveAnchor anchor);

    int updateAnchor(DmsLiveAnchor anchor);

    int updateAnchorStatus(@Param("tenantId") Long tenantId, @Param("id") Long id,
                           @Param("status") Integer status, @Param("expectedVersion") Integer expectedVersion);

    int touchAnchorLiveTime(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int insertComment(DmsLiveComment comment);

    List<DmsLiveComment> selectPublicComments(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                                              @Param("afterId") Long afterId, @Param("limit") Integer limit);

    List<DmsLiveComment> selectAdminComments(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                                             @Param("status") Integer status, @Param("limit") Integer limit);

    int updateCommentStatus(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status);

    int updateViewSession(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                          @Param("visitorId") String visitorId, @Param("userId") Long userId,
                          @Param("durationSeconds") Integer durationSeconds);

    int insertViewSession(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                          @Param("visitorId") String visitorId, @Param("userId") Long userId,
                          @Param("durationSeconds") Integer durationSeconds);

    int insertEvent(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                    @Param("visitorId") String visitorId, @Param("userId") Long userId,
                    @Param("eventType") String eventType, @Param("productId") Long productId);

    List<Long> selectReservedRoomIds(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int upsertReservation(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                          @Param("userId") Long userId);

    int cancelReservation(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                          @Param("userId") Long userId);

    Long selectRecentAttributionRoom(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                     @Param("productIds") List<Long> productIds,
                                     @Param("since") java.time.LocalDateTime since);

    Long countCurrentViewers(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                             @Param("activeSince") java.time.LocalDateTime activeSince);

    LiveAnalyticsVO selectAnalytics(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId,
                                    @Param("activeSince") java.time.LocalDateTime activeSince);
}
