package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsLiveRoomDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.LiveAnchorSaveDTO;
import com.macro.mall.distribution.dto.LiveCommentSubmitDTO;
import com.macro.mall.distribution.dto.LiveEngagementDTO;
import com.macro.mall.distribution.dto.LiveRoomSaveDTO;
import com.macro.mall.distribution.dto.TencentLiveCallbackDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.LiveAnalyticsVO;
import com.macro.mall.distribution.vo.LiveAnchorVO;
import com.macro.mall.distribution.vo.LiveRoomVO;
import com.macro.mall.distribution.vo.LiveStreamCredentialVO;
import com.macro.mall.distribution.vo.LiveStudioVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LiveOperationsTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsLiveRoomDao liveRoomDao;
    @Autowired private LiveRoomService liveRoomService;
    @Autowired private LiveCallbackService liveCallbackService;

    @BeforeEach
    void createAnchorMember() {
        jdbcTemplate.update("INSERT INTO dms_shop_member(user_id,phone,login_account,password_hash,nickname,status,system_account) VALUES(?,?,?,?,?,?,?)",
                99001L, "13900009901", "factory-anchor", "test-password-hash", "工厂讲解员", 1, 0);
    }

    @Test
    void platformAuthorizationStartInteractionAnalyticsAndStopFormAClosedLoop() {
        LiveAnchorSaveDTO anchorDto = new LiveAnchorSaveDTO();
        anchorDto.setMemberAccount("factory-anchor");
        anchorDto.setDisplayName("工厂一号直播员");
        anchorDto.setAnchorType("FACTORY");
        anchorDto.setCompanyName("测试工厂");
        LiveAnchorVO anchor = liveRoomService.saveAnchor(null, anchorDto);
        assertEquals("可开播", anchor.getStatusLabel());

        LiveRoomSaveDTO roomDto = new LiveRoomSaveDTO();
        roomDto.setTitle("工厂生产线直播");
        roomDto.setSubtitle("展示生产和包装过程");
        roomDto.setCoverUrl("https://images.example.com/factory-live.jpg");
        roomDto.setAnchorId(anchor.getAnchor().getId());
        roomDto.setLiveType("FACTORY");
        roomDto.setProviderCode("EXTERNAL");
        roomDto.setWatchUrl("https://live.example.com/factory/index.m3u8");
        roomDto.setCommentEnabled(1);
        roomDto.setShareEnabled(1);
        roomDto.setScheduledStartTime(LocalDateTime.now());
        roomDto.setScheduledEndTime(LocalDateTime.now().plusHours(8));
        roomDto.setStatus(1);
        roomDto.setProductIds(List.of(1L));
        LiveRoomVO room = liveRoomService.save(null, roomDto);

        roomDto.setStatus(2);
        assertThrows(RuntimeException.class, () -> liveRoomService.save(room.getRoom().getId(), roomDto));
        roomDto.setStatus(1);

        DmsShopMember member = memberDao.selectByUserId(99001L);
        LiveStreamCredentialVO credential = liveRoomService.start(room.getRoom().getId(), member);
        assertNull(credential.getPushUrl());
        assertEquals("https://live.example.com/factory/index.m3u8", credential.getPlaybackUrl());
        assertEquals(2, liveRoomDao.selectById(1L, room.getRoom().getId()).getStatus());

        String visitorId = UUID.randomUUID().toString();
        LiveEngagementDTO enter = engagement(visitorId, "ENTER", null, 0);
        liveRoomService.recordEngagement(room.getRoom().getId(), member, enter);
        liveRoomService.recordEngagement(room.getRoom().getId(), member, engagement(visitorId, "HEARTBEAT", null, 75));
        liveRoomService.recordEngagement(room.getRoom().getId(), member, engagement(visitorId, "SHARE", null, 75));
        liveRoomService.recordEngagement(room.getRoom().getId(), member, engagement(visitorId, "PRODUCT_CLICK", 1L, 75));

        LiveCommentSubmitDTO comment = new LiveCommentSubmitDTO();
        comment.setVisitorId(visitorId);
        comment.setContent("生产过程看得很清楚");
        liveRoomService.submitComment(room.getRoom().getId(), member, comment);

        LiveAnalyticsVO analytics = liveRoomService.getAnalytics(room.getRoom().getId());
        assertEquals(1, analytics.getUniqueViewers());
        assertEquals(1, analytics.getCurrentViewers());
        assertEquals(75, analytics.getAverageDurationSeconds());
        assertEquals(1, analytics.getShareCount());
        assertEquals(1, analytics.getCommentCount());
        assertEquals(1, analytics.getProductClickCount());
        assertEquals(room.getRoom().getId(), liveRoomService.resolveRecentAttribution(1L, 99001L, List.of(1L)));

        assertTrue(liveRoomService.stop(room.getRoom().getId(), member));
        assertEquals(3, liveRoomDao.selectById(1L, room.getRoom().getId()).getStatus());
        LiveStudioVO studio = liveRoomService.getStudio(member);
        assertFalse(studio.getRooms().isEmpty());
        assertNull(studio.getRooms().get(0).getProducts().get(0).getCostAmount());
        assertNull(studio.getRooms().get(0).getProducts().get(0).getBvValue());
        assertNull(studio.getRooms().get(0).getProducts().get(0).getSafetyStock());
    }

    @Test
    void tencentCallbackRequiresValidSignatureAndAdvancesTheRoomIdempotently() throws Exception {
        LiveAnchorSaveDTO anchorDto = new LiveAnchorSaveDTO();
        anchorDto.setMemberAccount("factory-anchor");
        anchorDto.setDisplayName("腾讯云测试主播");
        anchorDto.setAnchorType("PRODUCT");
        LiveAnchorVO anchor = liveRoomService.saveAnchor(null, anchorDto);

        LiveRoomSaveDTO roomDto = new LiveRoomSaveDTO();
        roomDto.setTitle("腾讯云回调测试直播");
        roomDto.setCoverUrl("https://images.example.com/tencent-live.jpg");
        roomDto.setAnchorId(anchor.getAnchor().getId());
        roomDto.setLiveType("PRODUCT");
        roomDto.setProviderCode("TENCENT");
        roomDto.setCommentEnabled(1);
        roomDto.setShareEnabled(1);
        roomDto.setScheduledStartTime(LocalDateTime.now());
        roomDto.setScheduledEndTime(LocalDateTime.now().plusHours(1));
        roomDto.setStatus(1);
        roomDto.setProductIds(List.of(1L));
        LiveRoomVO room = liveRoomService.save(null, roomDto);
        assertEquals(1, liveRoomDao.startRoom(1L, room.getRoom().getId(), anchor.getAnchor().getId(),
                "https://play.example.com/live/" + room.getRoom().getStreamName() + ".m3u8", room.getRoom().getVersion()));
        assertNull(liveRoomDao.selectById(1L, room.getRoom().getId()).getActualStartTime());
        assertEquals("CONNECTING", liveRoomService.listAdmin(null).stream()
                .filter(item -> item.getRoom().getId().equals(room.getRoom().getId()))
                .findFirst().orElseThrow().getRoomState());

        String callbackKey = "live-callback-test-key-2026";
        ReflectionTestUtils.setField(liveCallbackService, "callbackAuthKey", callbackKey);
        long expiresAt = Instant.now().plusSeconds(600).getEpochSecond();
        TencentLiveCallbackDTO callback = new TencentLiveCallbackDTO();
        callback.setEventType(1);
        callback.setStreamId(room.getRoom().getStreamName());
        callback.setT(expiresAt);
        callback.setSign(HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest((callbackKey + expiresAt).getBytes(StandardCharsets.UTF_8))));

        assertTrue(liveCallbackService.handleTencent(callback));
        assertTrue(liveCallbackService.handleTencent(callback));
        assertEquals(2, liveRoomDao.selectById(1L, room.getRoom().getId()).getStatus());
        assertNotNull(liveRoomDao.selectById(1L, room.getRoom().getId()).getActualStartTime());
        assertEquals("LIVE", liveRoomService.listAdmin(null).stream()
                .filter(item -> item.getRoom().getId().equals(room.getRoom().getId()))
                .findFirst().orElseThrow().getRoomState());
    }

    private LiveEngagementDTO engagement(String visitorId, String type, Long productId, int duration) {
        LiveEngagementDTO dto = new LiveEngagementDTO();
        dto.setVisitorId(visitorId);
        dto.setEventType(type);
        dto.setProductId(productId);
        dto.setDurationSeconds(duration);
        return dto;
    }
}
