package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsLiveRoomDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.dto.ProductNewArrivalDTO;
import com.macro.mall.distribution.service.impl.TenantDisplayConfigSupport;
import com.macro.mall.distribution.vo.LiveRoomVO;
import com.macro.mall.distribution.vo.ShopBrandCultureVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LiveRoomFoundationTest {

    @Autowired private DmsLiveRoomDao liveRoomDao;
    @Autowired private DmsShopProductDao productDao;
    @Autowired private DmsTenantDisplayConfigDao displayConfigDao;
    @Autowired private TenantDisplayConfigSupport displayConfigSupport;
    @Autowired private LiveRoomService liveRoomService;
    @Autowired private ShopService shopService;
    @Autowired private TenantService tenantService;

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

    @Test
    void newArrivalsMasterSwitchIsIndependentFromLiveSquare() {
        DmsTenantDisplayConfig config = displayConfigSupport.prepareForRead(displayConfigDao.selectByTenantId(1L), 1L);
        config.setLiveSquareEnabled(1);
        config.setNewArrivalsEnabled(0);
        displayConfigSupport.prepareForSave(config);
        displayConfigDao.update(config);

        assertTrue(shopService.listNewArrivals(1L, 20).isEmpty());
        assertTrue(productDao.selectNewArrivals(1L, LocalDateTime.now().minusDays(1), 20).size() > 0);
    }

    @Test
    void operatorCanAddAnyOnSaleProductForThirtyDaysOrPermanentlyWithoutChangingSaleStatus() {
        ProductNewArrivalDTO timed = new ProductNewArrivalDTO();
        timed.setEnabled(true);
        timed.setDurationDays(30);

        var updated = shopService.updateProductNewArrival(1L, timed);

        assertEquals(1, updated.getManualNewArrivalEnabled());
        assertTrue(updated.getManualNewArrivalEndTime().isAfter(LocalDateTime.now().plusDays(29)));
        assertEquals(1, updated.getStatus());
        assertEquals(List.of(1L), productDao.selectNewArrivals(1L, LocalDateTime.now().plusDays(1), 20)
                .stream().map(item -> item.getId()).toList());

        ProductNewArrivalDTO permanent = new ProductNewArrivalDTO();
        permanent.setEnabled(true);
        permanent.setDurationDays(0);
        updated = shopService.updateProductNewArrival(1L, permanent);
        assertNull(updated.getManualNewArrivalEndTime());

        ProductNewArrivalDTO invalid = new ProductNewArrivalDTO();
        invalid.setEnabled(true);
        invalid.setDurationDays(29);
        assertThrows(RuntimeException.class, () -> shopService.updateProductNewArrival(1L, invalid));
    }

    @Test
    void disabledBrandCultureDoesNotExposeContentAndEnabledPageReturnsLegacyContentOrOrderedImages() {
        ShopBrandCultureVO disabled = shopService.getBrandCulture(1L);
        assertEquals(false, disabled.getEnabled());
        assertNull(disabled.getContent());
        assertNull(disabled.getDetailImages());

        DmsTenant tenant = tenantService.getTenant(1L);
        tenant.setBrandCultureEnabled(1);
        tenant.setBrandCultureTitle("关于商城");
        tenant.setBrandCultureSubtitle("长期主义与真实服务");
        tenant.setBrandCultureContent("第一段\n第二段");
        tenantService.saveTenant(tenant);

        ShopBrandCultureVO enabled = shopService.getBrandCulture(1L);
        assertEquals(true, enabled.getEnabled());
        assertEquals("关于商城", enabled.getTitle());
        assertEquals("第一段\n第二段", enabled.getContent());
        assertTrue(enabled.getDetailImages().isEmpty());

        DmsTenantDisplayConfig display = displayConfigDao.selectByTenantId(1L);
        display.setExtraConfigJson("{\"brandCultureDetailImages\":["
                + "{\"url\":\"/api/shop/media/brand-culture/1/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg\",\"size\":100},"
                + "{\"url\":\"/api/shop/media/brand-culture/1/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png\",\"size\":200}]}");
        displayConfigDao.update(display);
        ShopBrandCultureVO withImages = shopService.getBrandCulture(1L);
        assertEquals(List.of(
                "/api/shop/media/brand-culture/1/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg",
                "/api/shop/media/brand-culture/1/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png"), withImages.getDetailImages());
        assertEquals("第一段\n第二段", withImages.getContent());

        tenant.setBrandCultureTitle(null);
        tenant.setBrandCultureSubtitle(null);
        tenantService.saveTenant(tenant);
        assertEquals(true, shopService.getBrandCulture(1L).getEnabled());
    }

    @Test
    void liveSquareMasterSwitchHidesListAndRejectsDirectDetailsWithoutDeletingRooms() {
        DmsLiveRoom room = room(1L, 2, "https://live.example.com/watch/closed-switch");
        liveRoomDao.insert(room);
        DmsTenantDisplayConfig config = displayConfigSupport.prepareForRead(displayConfigDao.selectByTenantId(1L), 1L);
        config.setLiveSquareEnabled(0);
        displayConfigSupport.prepareForSave(config);
        displayConfigDao.update(config);

        assertTrue(liveRoomService.listPublic(10).isEmpty());
        assertThrows(RuntimeException.class, () -> liveRoomService.getPublic(room.getId()));
        assertEquals(1, liveRoomDao.selectAdminList(1L, 2).stream().filter(item -> room.getId().equals(item.getId())).count());
    }

    @Test
    void memberCanReserveAndCancelOnlyUpcomingLiveRoomsIdempotently() {
        DmsLiveRoom upcoming = room(1L, 1, "https://live.example.com/watch/reservation");
        liveRoomDao.insert(upcoming);
        liveRoomDao.insertProduct(1L, upcoming.getId(), 1L, 1);
        DmsShopMember member = new DmsShopMember();
        member.setUserId(1001L);

        assertTrue(liveRoomService.reserve(upcoming.getId(), member));
        assertTrue(liveRoomService.reserve(upcoming.getId(), member));
        assertEquals(List.of(upcoming.getId()), liveRoomService.listReservations(member));

        assertTrue(liveRoomService.cancelReservation(upcoming.getId(), member));
        assertTrue(liveRoomService.listReservations(member).isEmpty());

        DmsLiveRoom live = room(1L, 2, "https://live.example.com/watch/live-now");
        liveRoomDao.insert(live);
        assertThrows(RuntimeException.class, () -> liveRoomService.reserve(live.getId(), member));
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
