package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsLiveRoomDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.vo.LiveRoomVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LiveRoomFoundationTest {

    @Autowired private DmsLiveRoomDao liveRoomDao;
    @Autowired private DmsShopProductDao productDao;
    @Autowired private LiveRoomService liveRoomService;

    @Test
    void publicLiveRoomIsTenantScopedAndDoesNotExposeUpcomingWatchUrl() {
        DmsLiveRoom room = room(1L, 1, "https://live.example.com/watch/1001");
        liveRoomDao.insert(room);
        liveRoomDao.insertProduct(1L, room.getId(), 1L, 1);

        List<LiveRoomVO> result = liveRoomService.listPublic(10);

        LiveRoomVO publicRoom = result.stream().filter(item -> room.getId().equals(item.getRoom().getId())).findFirst().orElseThrow();
        assertEquals("UPCOMING", publicRoom.getRoomState());
        assertNull(publicRoom.getRoom().getWatchUrl());
        assertNull(publicRoom.getRoom().getTenantId());
        assertNull(publicRoom.getRoom().getVersion());
        assertEquals(List.of(1L), publicRoom.getProducts().stream().map(product -> product.getId()).toList());
        assertTrue(liveRoomDao.selectPublicList(2L, 10).isEmpty());
    }

    @Test
    void newArrivalsUseFirstPublishTimeAndExcludeOldProducts() {
        assertEquals(2, productDao.selectNewArrivals(1L, LocalDateTime.now().minusDays(1), 20).size());
        assertTrue(productDao.selectNewArrivals(1L, LocalDateTime.now().plusDays(1), 20).isEmpty());
    }

    private DmsLiveRoom room(Long tenantId, Integer status, String watchUrl) {
        DmsLiveRoom room = new DmsLiveRoom();
        room.setTenantId(tenantId);
        room.setTitle("新品开箱直播");
        room.setSubtitle("真实商品讲解");
        room.setCoverUrl("https://images.example.com/live-cover.jpg");
        room.setAnchorName("灵启选品官");
        room.setWatchUrl(watchUrl);
        room.setScheduledStartTime(LocalDateTime.now().plusHours(1));
        room.setScheduledEndTime(LocalDateTime.now().plusHours(2));
        room.setStatus(status);
        room.setViewerCount(0);
        room.setHeatCount(100);
        room.setSortOrder(10);
        return room;
    }
}
