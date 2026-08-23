package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsLiveRoomDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dto.LiveRoomSaveDTO;
import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.service.LiveRoomService;
import com.macro.mall.distribution.service.MerchantProductReviewService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import com.macro.mall.distribution.vo.LiveRoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.macro.mall.distribution.util.ShopPublicViewSanitizer.product;

@Service
@RequiredArgsConstructor
public class LiveRoomServiceImpl implements LiveRoomService {

    private static final Set<Integer> VALID_STATUSES = Set.of(0, 1, 2, 3, 4);

    private final DmsLiveRoomDao liveRoomDao;
    private final DmsShopProductDao productDao;
    private final MerchantProductReviewService merchantProductReviewService;
    private final ShopCatalogCacheService catalogCache;
    private final OperationLogService operationLogService;

    @Override
    public List<LiveRoomVO> listPublic(int limit) {
        return listPublic(TenantContext.getTenantId(), limit);
    }

    @Override
    public List<LiveRoomVO> listPublic(Long tenantId, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        return liveRoomDao.selectPublicList(tenantId, safeLimit).stream()
                .map(room -> toVo(room, true))
                .toList();
    }

    @Override
    public LiveRoomVO getPublic(Long id) {
        DmsLiveRoom room = liveRoomDao.selectById(TenantContext.getTenantId(), id);
        if (room == null || room.getStatus() == null || !List.of(1, 2, 3).contains(room.getStatus())) {
            Asserts.fail("直播间不存在或暂未公开");
        }
        return toVo(room, true);
    }

    @Override
    public List<LiveRoomVO> listAdmin(Integer status) {
        assertPlatformOperator();
        if (status != null && !VALID_STATUSES.contains(status)) Asserts.fail("直播状态不正确");
        return liveRoomDao.selectAdminList(TenantContext.getTenantId(), status).stream()
                .map(room -> toVo(room, false))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiveRoomVO save(Long id, LiveRoomSaveDTO dto) {
        assertPlatformOperator();
        Long tenantId = TenantContext.getTenantId();
        DmsLiveRoom existing = id == null ? null : liveRoomDao.selectByIdForUpdate(tenantId, id);
        if (id != null && existing == null) Asserts.fail("直播间不存在或已被删除");
        Integer beforeStatus = existing == null ? null : existing.getStatus();

        int status = dto.getStatus() == null ? 0 : dto.getStatus();
        if (!VALID_STATUSES.contains(status)) Asserts.fail("直播状态不正确");
        if (dto.getScheduledEndTime() != null && !dto.getScheduledEndTime().isAfter(dto.getScheduledStartTime())) {
            Asserts.fail("计划结束时间必须晚于开播时间");
        }
        String coverUrl = normalizeMediaUrl(dto.getCoverUrl(), "直播封面");
        String watchUrl = normalizeWatchUrl(dto.getWatchUrl());
        List<Long> productIds = normalizeProductIds(dto.getProductIds());
        if ((status == 1 || status == 2) && productIds.isEmpty()) {
            Asserts.fail("公开预告或直播中状态至少要关联一个在售商品");
        }
        if (status == 2 && watchUrl == null) {
            Asserts.fail("切换为直播中前，请填写服务商提供的 HTTPS 观看地址");
        }
        validateProducts(tenantId, productIds, status == 1 || status == 2);

        DmsLiveRoom room = existing == null ? new DmsLiveRoom() : existing;
        room.setTenantId(tenantId);
        room.setTitle(dto.getTitle().trim());
        room.setSubtitle(trimToNull(dto.getSubtitle()));
        room.setCoverUrl(coverUrl);
        room.setAnchorName(trimToNull(dto.getAnchorName()));
        room.setWatchUrl(watchUrl);
        room.setScheduledStartTime(dto.getScheduledStartTime());
        room.setScheduledEndTime(dto.getScheduledEndTime());
        room.setStatus(status);
        room.setViewerCount(dto.getViewerCount() == null ? 0 : dto.getViewerCount());
        room.setHeatCount(dto.getHeatCount() == null ? 0 : dto.getHeatCount());
        room.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());

        if (existing == null) {
            liveRoomDao.insert(room);
        } else if (liveRoomDao.update(room) <= 0) {
            Asserts.fail("直播间已被其他管理员更新，请刷新后重试");
        }
        replaceProducts(tenantId, room.getId(), productIds);
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", existing == null ? "CREATE" : "UPDATE", "LIVE_ROOM",
                String.valueOf(room.getId()),
                beforeStatus == null ? null : "status=" + beforeStatus,
                "status=" + status + ",products=" + productIds.size(),
                existing == null ? "创建直播间" : "更新直播间资料");
        return toVo(liveRoomDao.selectById(tenantId, room.getId()), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long id, Integer status) {
        assertPlatformOperator();
        if (status == null || !VALID_STATUSES.contains(status)) Asserts.fail("直播状态不正确");
        Long tenantId = TenantContext.getTenantId();
        DmsLiveRoom room = liveRoomDao.selectByIdForUpdate(tenantId, id);
        if (room == null) Asserts.fail("直播间不存在或已被删除");
        List<Long> productIds = liveRoomDao.selectProductIds(tenantId, id);
        if ((status == 1 || status == 2) && productIds.isEmpty()) {
            Asserts.fail("公开预告或直播中状态至少要关联一个在售商品");
        }
        if (status == 2 && trimToNull(room.getWatchUrl()) == null) {
            Asserts.fail("切换为直播中前，请先配置 HTTPS 观看地址");
        }
        validateProducts(tenantId, productIds, status == 1 || status == 2);
        int before = room.getStatus() == null ? 0 : room.getStatus();
        if (before == status) return true;
        if (liveRoomDao.updateStatus(tenantId, id, status, room.getVersion()) <= 0) {
            Asserts.fail("直播间状态已发生变化，请刷新后重试");
        }
        catalogCache.invalidateAfterCommit(tenantId);
        operationLogService.log("LIVE_ROOM", "STATUS", "LIVE_ROOM", String.valueOf(id),
                "status=" + before, "status=" + status, "更新直播间状态");
        return true;
    }

    private LiveRoomVO toVo(DmsLiveRoom room, boolean publicView) {
        Long tenantId = room.getTenantId();
        List<Long> productIds = liveRoomDao.selectProductIds(tenantId, room.getId());
        List<DmsShopProduct> products = new ArrayList<>();
        for (Long productId : productIds) {
            DmsShopProduct item = productDao.selectByIdScoped(tenantId, productId);
            if (item == null) continue;
            if (publicView && (!Integer.valueOf(1).equals(item.getStatus())
                    || !Integer.valueOf(1).equals(item.getNormalSaleEnabled()))) continue;
            if (publicView) product(item, false);
            products.add(item);
        }
        LiveRoomVO vo = new LiveRoomVO();
        vo.setRoom(room);
        vo.setRoomState(resolveState(room));
        vo.setProducts(products);
        vo.setProductIds(publicView ? null : productIds);
        if (publicView) {
            room.setTenantId(null);
            room.setVersion(null);
            room.setCreateTime(null);
            room.setUpdateTime(null);
            if ("UPCOMING".equals(vo.getRoomState())) room.setWatchUrl(null);
        }
        return vo;
    }

    private String resolveState(DmsLiveRoom room) {
        Integer status = room.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (status == null || status == 0) return "DRAFT";
        if (status == 4) return "DISABLED";
        if (status == 3 || (status == 2 && room.getScheduledEndTime() != null
                && !now.isBefore(room.getScheduledEndTime()))) return "ENDED";
        if (status == 2) return "LIVE";
        return "UPCOMING";
    }

    private void replaceProducts(Long tenantId, Long roomId, List<Long> productIds) {
        liveRoomDao.deleteProducts(tenantId, roomId);
        for (int i = 0; i < productIds.size(); i++) {
            liveRoomDao.insertProduct(tenantId, roomId, productIds.get(i), i + 1);
        }
    }

    private void validateProducts(Long tenantId, List<Long> productIds, boolean requireActive) {
        for (Long productId : productIds) {
            DmsShopProduct product = productDao.selectByIdScoped(tenantId, productId);
            if (product == null) Asserts.fail("关联商品不存在：" + productId);
            if (requireActive && (!Integer.valueOf(1).equals(product.getStatus())
                    || !Integer.valueOf(1).equals(product.getNormalSaleEnabled()))) {
                Asserts.fail("公开直播只能关联正常商城的在售商品：" + product.getProductName());
            }
        }
    }

    private List<Long> normalizeProductIds(List<Long> source) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (source != null) {
            for (Long id : source) {
                if (id == null || id <= 0) Asserts.fail("关联商品编号不正确");
                ids.add(id);
            }
        }
        if (ids.size() > 20) Asserts.fail("单个直播间最多关联20个商品");
        return new ArrayList<>(ids);
    }

    private String normalizeMediaUrl(String raw, String fieldName) {
        String value = trimToNull(raw);
        if (value == null) Asserts.fail(fieldName + "不能为空");
        if (value.startsWith("/api/shop/media/images/") || value.startsWith("/shop/media/images/")) return value;
        return requireHttpsUrl(value, fieldName);
    }

    private String normalizeWatchUrl(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : requireHttpsUrl(value, "观看地址");
    }

    private String requireHttpsUrl(String value, String fieldName) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                Asserts.fail(fieldName + "必须是无账号信息的 HTTPS 地址");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            Asserts.fail(fieldName + "格式不正确");
            return null;
        }
    }

    private void assertPlatformOperator() {
        if (merchantProductReviewService.currentMerchantId() != null) {
            Asserts.fail("直播广场当前由平台统一运营，商户账号无权修改");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
